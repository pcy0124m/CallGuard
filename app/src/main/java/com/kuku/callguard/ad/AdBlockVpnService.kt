package com.kuku.callguard.ad

import android.app.Notification
import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import com.kuku.callguard.data.Db
import com.kuku.callguard.data.Prefs
import com.kuku.callguard.util.Notifier
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * 广告拦截服务：本地 VPN + DNS 过滤。
 *
 * 原理：建立本地 VPN，把系统 DNS 指向 VPN 内的假 DNS 地址（10.111.222.3），
 * 只把该地址的路由收进 TUN 网卡，因此 VPN 只会收到 DNS 查询包：
 *  - 查询域名命中广告域名黑名单 → 直接返回 0.0.0.0，广告加载失败（即被拦截）；
 *  - 未命中 → 转发到上游真实 DNS（223.5.5.5），返回正常结果。
 * 拦截到的域名按域名聚合计数，可在主界面"广告拦截"页查看。
 */
class AdBlockVpnService : VpnService() {

    companion object {
        const val ACTION_STOP = "com.kuku.callguard.ad.STOP"
        private const val TAG = "AdBlockVpn"
        private const val FAKE_DNS = "10.111.222.3"
        private const val UPSTREAM_DNS = "223.5.5.5"

        /** 内置广告/追踪域名黑名单（后缀匹配），可在界面中追加自定义域名 */
        val BASE_BLOCKLIST = listOf(
            "doubleclick.net", "googlesyndication.com", "googleadservices.com",
            "admob.com", "google-analytics.com", "googletagmanager.com",
            "adcolony.com", "applovin.com", "inmobi.com", "mopub.com",
            "tapjoy.com", "unityads.unity3d.com", "vungle.com", "chartboost.com",
            "ironsrc.com", "appsflyer.com", "adjust.com",
            "umeng.com", "umengcloud.com", "tanx.com", "admaster.com.cn",
            "adview.cn", "domob.cn", "talkingdata.com"
        )

        @Volatile
        var isRunning = false
            private set
    }

    private var tun: ParcelFileDescriptor? = null
    private var worker: Thread? = null
    private val pool = Executors.newCachedThreadPool()
    private val blockCache = ConcurrentHashMap<String, Boolean>()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopVpn()
            stopSelf()
            return START_NOT_STICKY
        }
        if (tun == null) startVpn()
        return START_STICKY
    }

    private fun startVpn() {
        val fd = Builder()
            .setSession("拦截助手 · 广告拦截")
            .addAddress("10.111.222.1", 32)
            .addDnsServer(FAKE_DNS)
            .addRoute(FAKE_DNS, 32)   // 只路由假 DNS 地址，其余流量不经过 VPN
            .setMtu(1500)
            .establish()
        if (fd == null) {
            Log.e(TAG, "建立 VPN 失败")
            stopSelf()
            return
        }
        tun = fd
        isRunning = true
        startForeground(Notifier.AD_NOTIFICATION_ID, Notifier.buildAdNotification(this))
        worker = Thread { loop(fd) }.apply { start() }
    }

    private fun loop(fd: ParcelFileDescriptor) {
        val input = FileInputStream(fd.fileDescriptor)
        val output = FileOutputStream(fd.fileDescriptor)
        val buf = ByteArray(32767)
        while (isRunning) {
            val len = try {
                input.read(buf)
            } catch (e: Exception) {
                break
            }
            if (len <= 0) continue
            val packet = buf.copyOf(len)
            pool.execute { handle(packet, output) }
        }
    }

    /** 解析 IP/UDP 包，处理其中的 DNS 查询 */
    private fun handle(packet: ByteArray, output: FileOutputStream) {
        try {
            if (packet.size < 28) return
            if ((packet[0].toInt() and 0xF0) != 0x40) return          // 仅 IPv4
            val ihl = (packet[0].toInt() and 0x0F) * 4
            if (packet.size < ihl + 8) return
            if ((packet[9].toInt() and 0xFF) != 17) return            // 仅 UDP
            val srcIp = packet.copyOfRange(12, 16)
            val srcPort = u16(packet, ihl)
            val dstPort = u16(packet, ihl + 2)
            if (dstPort != 53) return

            val dns = packet.copyOfRange(ihl + 8, packet.size)
            if (dns.size < 18) return
            val domain = parseDomain(dns) ?: return

            if (isBlocked(domain)) {
                recordBlocked(domain)
                sendResponse(output, inet(FAKE_DNS).address, srcIp, 53, srcPort, buildBlockedResponse(dns))
            } else {
                forward(dns, srcIp, srcPort, output)
            }
        } catch (e: Exception) {
            Log.w(TAG, "处理数据包异常: ${e.message}")
        }
    }

    /** 未命中黑名单：转发到上游 DNS 并把结果写回 TUN */
    private fun forward(dns: ByteArray, clientIp: ByteArray, clientPort: Int, output: FileOutputStream) {
        try {
            DatagramSocket().use { socket ->
                protect(socket)
                socket.soTimeout = 5000
                socket.send(DatagramPacket(dns, dns.size, InetSocketAddress(inet(UPSTREAM_DNS), 53)))
                val recv = ByteArray(4096)
                val pkt = DatagramPacket(recv, recv.size)
                socket.receive(pkt)
                sendResponse(output, inet(FAKE_DNS).address, clientIp, 53, clientPort, recv.copyOf(pkt.length))
            }
        } catch (e: Exception) {
            Log.w(TAG, "DNS 转发失败: ${e.message}")
        }
    }

    /** 命中黑名单：基于原查询构造 A 记录为 0.0.0.0 的应答 */
    private fun buildBlockedResponse(query: ByteArray): ByteArray {
        val resp = query.copyOf(query.size + 16)
        resp[2] = (resp[2].toInt() or 0x80).toByte()                    // QR=1 应答
        resp[3] = ((resp[3].toInt() or 0x80) and 0xFC).toByte()         // RA=1, RCODE=0
        resp[6] = 0; resp[7] = 1                                        // ANCOUNT=1
        resp[8] = 0; resp[9] = 0                                        // NSCOUNT=0
        resp[10] = 0; resp[11] = 0                                      // ARCOUNT=0
        val a = resp.size - 16
        resp[a] = 0xC0.toByte(); resp[a + 1] = 0x0C                     // 指向问题区域名
        resp[a + 2] = 0; resp[a + 3] = 1                                // Type A
        resp[a + 4] = 0; resp[a + 5] = 1                                // Class IN
        resp[a + 6] = 0; resp[a + 7] = 0; resp[a + 8] = 0; resp[a + 9] = 60  // TTL=60s
        resp[a + 10] = 0; resp[a + 11] = 4                              // RDLENGTH=4
        resp[a + 12] = 0; resp[a + 13] = 0; resp[a + 14] = 0; resp[a + 15] = 0 // 0.0.0.0
        return resp
    }

    /** 构造 IP+UDP 头并写入 TUN */
    private fun sendResponse(
        output: FileOutputStream,
        srcIp: ByteArray, dstIp: ByteArray,
        srcPort: Int, dstPort: Int,
        payload: ByteArray
    ) {
        val udpLen = 8 + payload.size
        val total = 20 + udpLen
        val out = ByteArray(total)
        out[0] = 0x45; out[1] = 0
        out[2] = (total shr 8).toByte(); out[3] = total.toByte()
        out[6] = 0x40                                                   // DF
        out[8] = 64; out[9] = 17                                        // TTL, UDP
        System.arraycopy(srcIp, 0, out, 12, 4)
        System.arraycopy(dstIp, 0, out, 16, 4)
        val ck = ipChecksum(out)
        out[10] = (ck shr 8).toByte(); out[11] = ck.toByte()
        out[20] = (srcPort shr 8).toByte(); out[21] = srcPort.toByte()
        out[22] = (dstPort shr 8).toByte(); out[23] = dstPort.toByte()
        out[24] = (udpLen shr 8).toByte(); out[25] = udpLen.toByte()
        System.arraycopy(payload, 0, out, 28, payload.size)
        synchronized(output) {
            output.write(out)
            output.flush()
        }
    }

    /** 解析 DNS 查询中的域名（仅支持不带压缩指针的查询名） */
    private fun parseDomain(dns: ByteArray): String? {
        var i = 12
        val sb = StringBuilder()
        while (i < dns.size) {
            val len = dns[i].toInt() and 0xFF
            if (len == 0) break
            if ((len and 0xC0) != 0 || i + 1 + len > dns.size) return null
            sb.append(String(dns, i + 1, len, Charsets.US_ASCII)).append('.')
            i += 1 + len
        }
        if (sb.isEmpty()) return null
        return sb.dropLast(1).toString().lowercase()
    }

    /** 后缀匹配黑名单（含用户自定义域名），带缓存 */
    private fun isBlocked(domain: String): Boolean {
        blockCache[domain]?.let { return it }
        val result = BASE_BLOCKLIST.any { domain == it || domain.endsWith(".$it") } ||
                Prefs.getAdDomains(this).any { domain == it || domain.endsWith(".$it") }
        blockCache[domain] = result
        return result
    }

    /** 拦截记录按域名聚合计数入库 */
    private fun recordBlocked(domain: String) {
        val ctx = applicationContext
        pool.execute {
            try {
                Db.get(ctx).upsertAd(domain, System.currentTimeMillis())
            } catch (e: Exception) {
                Log.w(TAG, "记录入库失败: ${e.message}")
            }
        }
    }

    private fun stopVpn() {
        isRunning = false
        try {
            worker?.interrupt()
            tun?.close()
        } catch (_: Exception) {
        }
        tun = null
        worker = null
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    override fun onRevoke() {
        stopVpn()
        stopSelf()
    }

    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }

    private fun u16(data: ByteArray, offset: Int): Int =
        ((data[offset].toInt() and 0xFF) shl 8) or (data[offset + 1].toInt() and 0xFF)

    private fun inet(addr: String): InetAddress = InetAddress.getByName(addr)

    private fun ipChecksum(header: ByteArray): Int {
        var sum = 0
        for (i in 0 until 20 step 2) {
            sum += ((header[i].toInt() and 0xFF) shl 8) or (header[i + 1].toInt() and 0xFF)
        }
        while (sum > 0xFFFF) sum = (sum and 0xFFFF) + (sum shr 16)
        return sum.inv() and 0xFFFF
    }
}
