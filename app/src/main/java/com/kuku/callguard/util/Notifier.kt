package com.kuku.callguard.util

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.kuku.callguard.MainActivity

/** 拦截记录通知 + 广告拦截前台服务通知（使用平台 Notification.Builder） */
object Notifier {

    const val AD_NOTIFICATION_ID = 1001
    private const val CHANNEL_RECORDS = "blocked_records"
    private const val CHANNEL_SERVICE = "ad_service"
    private const val RECORD_NOTIFICATION_BASE = 2000

    private fun ensureChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_RECORDS, "拦截记录提醒", NotificationManager.IMPORTANCE_DEFAULT)
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_SERVICE, "广告拦截服务", NotificationManager.IMPORTANCE_LOW)
        )
    }

    private fun contentIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    fun notifyCall(context: Context, number: String, reason: String) {
        ensureChannels(context)
        val notification = Notification.Builder(context, CHANNEL_RECORDS)
            .setSmallIcon(android.R.drawable.sym_call_missed)
            .setContentTitle("已拦截来电")
            .setContentText("$number（$reason）")
            .setContentIntent(contentIntent(context))
            .setAutoCancel(true)
            .build()
        notifySafe(context, RECORD_NOTIFICATION_BASE, notification)
    }

    fun notifySms(context: Context, sender: String, body: String, reason: String) {
        ensureChannels(context)
        val style = Notification.BigTextStyle().bigText("$body\n拦截原因：$reason")
        val notification = Notification.Builder(context, CHANNEL_RECORDS)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle("已拦截短信：$sender")
            .setContentText(body.take(40))
            .setStyle(style)
            .setContentIntent(contentIntent(context))
            .setAutoCancel(true)
            .build()
        notifySafe(context, RECORD_NOTIFICATION_BASE + 1, notification)
    }

    /** 广告拦截前台服务常驻通知 */
    fun buildAdNotification(context: Context): Notification {
        ensureChannels(context)
        return Notification.Builder(context, CHANNEL_SERVICE)
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setContentTitle("广告拦截运行中")
            .setContentText("正在通过本地 DNS 过滤广告域名，点击查看拦截记录")
            .setOngoing(true)
            .setContentIntent(contentIntent(context))
            .build()
    }

    /** 未授予通知权限时静默忽略，不影响拦截记录入库 */
    private fun notifySafe(context: Context, id: Int, notification: Notification) {
        try {
            context.getSystemService(NotificationManager::class.java)?.notify(id, notification)
        } catch (_: SecurityException) {
        }
    }
}
