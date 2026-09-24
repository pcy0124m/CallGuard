package com.kuku.callguard.call

import android.telecom.Call
import android.telecom.CallScreeningService
import com.kuku.callguard.data.Db
import com.kuku.callguard.data.Prefs
import com.kuku.callguard.util.Notifier
import kotlin.concurrent.thread

/**
 * 来电拦截服务（系统级）。
 * 生效前提：用户需在系统弹窗中把本应用设为"来电识别与骚扰拦截"应用。
 * 拦截到的来电会写入数据库并在主界面"电话拦截"标签页展示。
 */
class CallScreeningServiceImpl : CallScreeningService() {

    override fun onScreenCall(callDetails: Call.Details) {
        // 只处理打入的来电，去电不拦截
        if (!isIncoming(callDetails)) {
            respondAllow(callDetails)
            return
        }
        val number = callDetails.handle?.schemeSpecificPart ?: ""
        val reason = judge(number)

        if (reason == null) {
            respondAllow(callDetails)
        } else {
            // 拒接来电，且不写入系统通话记录、不弹系统通知
            respondToCall(
                callDetails,
                CallResponse.Builder()
                    .setDisallowCall(true)
                    .setRejectCall(true)
                    .setSkipCallLog(true)
                    .setSkipNotification(true)
                    .build()
            )
            val ctx = applicationContext
            thread {
                try {
                    Db.get(ctx).insertCall(number, reason, System.currentTimeMillis())
                    Notifier.notifyCall(ctx, number, reason)
                } catch (_: Exception) {
                }
            }
        }
    }

    private fun respondAllow(details: Call.Details) {
        respondToCall(details, CallResponse.Builder().build())
    }

    /**
     * 判断是否为打入的来电。
     * 框架方法在不同镜像中叫 getDirection() 或 getCallDirection()，用反射兼容两者。
     */
    private fun isIncoming(details: Call.Details): Boolean {
        val direction = try {
            Call.Details::class.java.getMethod("getDirection").invoke(details) as Int
        } catch (e: Exception) {
            try {
                Call.Details::class.java.getMethod("getCallDirection").invoke(details) as Int
            } catch (e: Exception) {
                Call.Details.DIRECTION_UNKNOWN
            }
        }
        return direction == Call.Details.DIRECTION_INCOMING
    }

    /** 判定是否拦截，返回拦截原因；返回 null 表示放行 */
    private fun judge(number: String): String? {
        if (number.isBlank()) return "未知号码"
        if (Prefs.getBlacklist(this).contains(number)) return "黑名单号码"
        if (Prefs.blockServiceNumbers(this)) {
            for (prefix in SERVICE_PREFIXES) {
                if (number.startsWith(prefix)) return "营销/客服号段（$prefix*）"
            }
        }
        return null
    }

    companion object {
        /** 常见营销/客服号段，可在设置中关闭 */
        val SERVICE_PREFIXES = listOf("400", "401", "95", "96", "1010", "1009")
    }
}
