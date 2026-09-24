package com.kuku.callguard.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.kuku.callguard.data.Db
import com.kuku.callguard.util.Notifier
import kotlin.concurrent.thread

/**
 * 短信拦截接收器。
 * 收到短信后先做垃圾识别：命中则写入拦截记录并弹通知（记录可在主界面"短信拦截"页查看）；
 * 未命中则不做任何处理，短信正常进入收件箱。
 *
 * 说明：受 Android 系统限制，只有"默认短信应用"才能阻止短信进入收件箱；
 * 本应用采用"识别 + 记录 + 通知"的方式实现拦截提醒，详见 README。
 */
class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        if (messages.isEmpty()) return

        val sender = messages.first().originatingAddress ?: "未知号码"
        // 长短信可能拆成多条 PDU，按顺序拼接
        val body = messages.joinToString("") { it.messageBody ?: "" }
        val reason = SpamFilter.judge(body) ?: return

        val pending = goAsync()
        val appContext = context.applicationContext
        thread {
            try {
                Db.get(appContext).insertSms(sender, body, reason, System.currentTimeMillis())
                Notifier.notifySms(appContext, sender, body, reason)
            } catch (_: Exception) {
            } finally {
                pending.finish()
            }
        }
    }
}
