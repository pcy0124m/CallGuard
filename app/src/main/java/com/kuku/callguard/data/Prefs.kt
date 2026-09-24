package com.kuku.callguard.data

import android.content.Context

/** 用户可配置项：来电黑名单、号段规则开关、自定义广告域名 */
object Prefs {
    private const val FILE = "callguard_prefs"

    private fun sp(context: Context) =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** 来电黑名单号码集合 */
    fun getBlacklist(context: Context): MutableSet<String> =
        sp(context).getStringSet("blacklist", emptySet())!!.toMutableSet()

    fun addBlacklist(context: Context, number: String) {
        val set = getBlacklist(context)
        set.add(number)
        sp(context).edit().putStringSet("blacklist", set).apply()
    }

    /** 是否拦截 400/95 等营销客服号段（默认开启） */
    fun blockServiceNumbers(context: Context): Boolean =
        sp(context).getBoolean("block_service_numbers", true)

    fun setBlockServiceNumbers(context: Context, value: Boolean) {
        sp(context).edit().putBoolean("block_service_numbers", value).apply()
    }

    /** 用户自定义广告域名 */
    fun getAdDomains(context: Context): Set<String> =
        sp(context).getStringSet("ad_domains", emptySet())!!

    fun addAdDomain(context: Context, domain: String) {
        val set = getAdDomains(context).toMutableSet()
        set.add(domain.lowercase().trim())
        sp(context).edit().putStringSet("ad_domains", set).apply()
    }
}
