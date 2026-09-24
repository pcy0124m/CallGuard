package com.kuku.callguard.sms

/**
 * 垃圾短信识别：基于关键词匹配。
 * 命中任一关键词即判定为广告/骚扰短信。
 */
object SpamFilter {

    private val KEYWORDS = listOf(
        "贷款", "借款", "额度", "放款", "免息",
        "中奖", "抽奖", "恭喜您", "点击领取",
        "退订回", "回复TD", "回复td", "回T退订",
        "推广", "营销", "广告",
        "代开发票", "发票代开",
        "兼职", "刷单",
        "加微信", "加V", "扫码进群"
    )

    /** 返回命中的拦截原因；null 表示正常短信，放行 */
    fun judge(content: String): String? {
        if (content.isBlank()) return null
        for (kw in KEYWORDS) {
            if (content.contains(kw)) return "命中关键词「$kw」"
        }
        return null
    }
}
