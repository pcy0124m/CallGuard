package com.kuku.callguard

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import com.kuku.callguard.ad.AdBlockVpnService
import com.kuku.callguard.data.Db
import com.kuku.callguard.data.Prefs
import com.kuku.callguard.ui.RecordAdapter
import com.kuku.callguard.ui.RecordItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 主界面：三个标签页分别展示【电话拦截 / 短信拦截 / 广告拦截】的拦截记录，
 * 顶部统计卡片实时显示三类拦截总量，并提供权限授权、黑名单管理、
 * 广告域名管理和清空记录入口。
 */
class MainActivity : Activity() {

    private lateinit var adapter: RecordAdapter
    private lateinit var tvStatus: TextView
    private lateinit var statCall: TextView
    private lateinit var statSms: TextView
    private lateinit var statAd: TextView
    private lateinit var tabButtons: List<TextView>
    private val db by lazy { Db.get(this) }
    private val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    private var currentTab = 0

    /** 每 3 秒刷新一次拦截记录，保证新拦截能及时显示 */
    private val refreshHandler = Handler(Looper.getMainLooper())
    private val refreshRunnable = object : Runnable {
        override fun run() {
            reloadRecords()
            refreshHandler.postDelayed(this, 3000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus = findViewById(R.id.tvStatus)
        statCall = findViewById(R.id.statCall)
        statSms = findViewById(R.id.statSms)
        statAd = findViewById(R.id.statAd)
        adapter = RecordAdapter(this)
        val list = findViewById<ListView>(R.id.list)
        list.adapter = adapter
        list.emptyView = findViewById(R.id.empty)

        val tabCall = findViewById<TextView>(R.id.tabCall)
        val tabSms = findViewById<TextView>(R.id.tabSms)
        val tabAd = findViewById<TextView>(R.id.tabAd)
        tabButtons = listOf(tabCall, tabSms, tabAd)
        tabCall.setOnClickListener { switchTab(0) }
        tabSms.setOnClickListener { switchTab(1) }
        tabAd.setOnClickListener { switchTab(2) }

        findViewById<TextView>(R.id.btnCallRole).setOnClickListener { requestCallScreeningRole() }
        findViewById<TextView>(R.id.btnAdBlock).setOnClickListener { toggleAdBlock() }
        findViewById<TextView>(R.id.btnAddBlacklist).setOnClickListener { showAddBlacklistDialog() }
        findViewById<TextView>(R.id.btnAddAdDomain).setOnClickListener { showAddAdDomainDialog() }
        findViewById<TextView>(R.id.btnClear).setOnClickListener { clearCurrentTab() }

        switchTab(0)
        requestSmsPermissions()
        requestCallScreeningRole()
        updateStatus()
    }

    override fun onResume() {
        super.onResume()
        refreshHandler.post(refreshRunnable)
        updateStatus()
    }

    override fun onPause() {
        super.onPause()
        refreshHandler.removeCallbacks(refreshRunnable)
    }

    private fun switchTab(index: Int) {
        currentTab = index
        adapter.icon = when (index) {
            0 -> "📞"; 1 -> "💬"; else -> "🚫"
        }
        tabButtons.forEachIndexed { i, tv ->
            if (i == index) {
                tv.setBackgroundResource(R.drawable.bg_tab_selected)
                tv.setTextColor(getColor(R.color.primary))
                tv.paint.isFakeBoldText = true
            } else {
                tv.background = null
                tv.setTextColor(getColor(R.color.text_gray))
                tv.paint.isFakeBoldText = false
            }
        }
        reloadRecords()
    }

    /** 请求短信接收权限 */
    private fun requestSmsPermissions() {
        requestPermissions(
            arrayOf(
                android.Manifest.permission.RECEIVE_SMS,
                android.Manifest.permission.READ_SMS
            ),
            REQ_SMS
        )
    }

    /** 请求"来电识别与骚扰拦截"系统角色 */
    private fun requestCallScreeningRole() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(android.app.role.RoleManager::class.java)
            if (roleManager != null &&
                roleManager.isRoleAvailable(android.app.role.RoleManager.ROLE_CALL_SCREENING) &&
                !roleManager.isRoleHeld(android.app.role.RoleManager.ROLE_CALL_SCREENING)
            ) {
                startActivityForResult(
                    roleManager.createRequestRoleIntent(android.app.role.RoleManager.ROLE_CALL_SCREENING),
                    REQ_ROLE
                )
            }
        }
    }

    /** 开启/停止广告拦截 VPN */
    private fun toggleAdBlock() {
        if (AdBlockVpnService.isRunning) {
            startService(Intent(this, AdBlockVpnService::class.java).setAction(AdBlockVpnService.ACTION_STOP))
            Toast.makeText(this, "广告拦截已停止", Toast.LENGTH_SHORT).show()
            updateStatus()
        } else {
            val intent = VpnService.prepare(this)
            if (intent != null) {
                startActivityForResult(intent, REQ_VPN)
            } else {
                startForegroundService(Intent(this, AdBlockVpnService::class.java))
                Toast.makeText(this, "广告拦截已开启", Toast.LENGTH_SHORT).show()
                updateStatus()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQ_ROLE -> {
                if (resultCode == RESULT_OK) {
                    Toast.makeText(this, "来电拦截已生效", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "未授权来电拦截，将无法拦截来电", Toast.LENGTH_SHORT).show()
                }
                updateStatus()
            }
            REQ_VPN -> {
                if (resultCode == RESULT_OK) {
                    startForegroundService(Intent(this, AdBlockVpnService::class.java))
                    Toast.makeText(this, "广告拦截已开启", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "未授予 VPN 权限，无法开启广告拦截", Toast.LENGTH_SHORT).show()
                }
                updateStatus()
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_SMS) {
            if (grantResults.isNotEmpty() && grantResults.all { it == android.content.pm.PackageManager.PERMISSION_GRANTED }) {
                Toast.makeText(this, "短信权限已授予，短信拦截已生效", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "缺少短信权限，无法拦截短信", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /** 添加来电黑名单号码 */
    private fun showAddBlacklistDialog() {
        val input = EditText(this).apply { hint = "输入要拦截的电话号码" }
        AlertDialog.Builder(this)
            .setTitle("添加黑名单")
            .setView(input)
            .setPositiveButton("添加") { _, _ ->
                val number = input.text.toString().trim()
                if (number.isNotEmpty()) {
                    Prefs.addBlacklist(this, number)
                    Toast.makeText(this, "已加入黑名单：$number", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    /** 添加自定义广告域名 */
    private fun showAddAdDomainDialog() {
        val input = EditText(this).apply { hint = "例如 ads.example.com" }
        AlertDialog.Builder(this)
            .setTitle("添加广告域名")
            .setView(input)
            .setPositiveButton("添加") { _, _ ->
                val domain = input.text.toString().trim()
                if (domain.isNotEmpty()) {
                    Prefs.addAdDomain(this, domain)
                    Toast.makeText(this, "已加入广告黑名单：$domain", Toast.LENGTH_SHORT).show()
                    reloadRecords()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    /** 清空当前标签页的拦截记录 */
    private fun clearCurrentTab() {
        Thread {
            when (currentTab) {
                0 -> db.clearCalls()
                1 -> db.clearSms()
                else -> db.clearAds()
            }
        }.start()
        Toast.makeText(this, "已清空", Toast.LENGTH_SHORT).show()
    }

    /** 读取拦截记录并刷新列表与统计卡片 */
    private fun reloadRecords() {
        Thread {
            try {
                val calls = db.listCalls()
                val sms = db.listSms()
                val ads = db.listAds()
                val items: List<RecordItem> = when (currentTab) {
                    0 -> calls.map {
                        RecordItem(
                            title = it.number,
                            reason = "拦截原因：${it.reason}",
                            subtitle = "来电已被自动拒接",
                            time = sdf.format(Date(it.time))
                        )
                    }
                    1 -> sms.map {
                        RecordItem(
                            title = it.sender,
                            reason = "拦截原因：${it.reason}",
                            subtitle = it.content,
                            time = sdf.format(Date(it.time))
                        )
                    }
                    else -> ads.map {
                        RecordItem(
                            title = it.domain,
                            reason = "已拦截 ${it.count} 次广告请求",
                            subtitle = "该域名 DNS 解析已被屏蔽",
                            time = "最近拦截：" + sdf.format(Date(it.lastTime))
                        )
                    }
                }
                runOnUiThread {
                    adapter.submit(items)
                    statCall.text = calls.size.toString()
                    statSms.text = sms.size.toString()
                    statAd.text = ads.size.toString()
                }
            } catch (_: Exception) {
            }
        }.start()
    }

    private fun updateStatus() {
        val roleHeld = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            getSystemService(android.app.role.RoleManager::class.java)
                ?.isRoleHeld(android.app.role.RoleManager.ROLE_CALL_SCREENING) == true
        } else false
        val adStatus = if (AdBlockVpnService.isRunning) "运行中" else "未开启"
        tvStatus.text = "来电拦截：${if (roleHeld) "✅ 已生效" else "⚠️ 未授权"}   短信拦截：✅ 识别中   广告拦截：$adStatus"
    }

    companion object {
        private const val REQ_SMS = 1
        private const val REQ_ROLE = 2
        private const val REQ_VPN = 3
    }
}
