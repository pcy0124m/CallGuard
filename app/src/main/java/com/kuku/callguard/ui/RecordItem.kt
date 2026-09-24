package com.kuku.callguard.ui

/** 拦截记录列表的统一条目模型 */
data class RecordItem(
    val title: String,
    val reason: String,
    val subtitle: String,
    val time: String
)
