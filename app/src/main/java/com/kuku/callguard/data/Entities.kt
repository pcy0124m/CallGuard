package com.kuku.callguard.data

/** 被拦截的来电记录 */
data class BlockedCall(
    val id: Long = 0,
    val number: String,
    val reason: String,
    val time: Long
)

/** 被拦截的短信记录 */
data class BlockedSms(
    val id: Long = 0,
    val sender: String,
    val content: String,
    val reason: String,
    val time: Long
)

/** 被拦截的广告域名（按域名聚合，count 为拦截次数） */
data class BlockedAd(
    val id: Long = 0,
    val domain: String,
    val count: Int = 1,
    val lastTime: Long
)
