package io.legado.app.help.book

/** Heuristics flag suspicious samples; they cannot establish whether a work is unabridged. */
internal fun contentQualityWarnings(content: String): List<String> {
    val text = content.trim()
    if (text.isEmpty()) return listOf("正文为空")
    val warnings = mutableListOf<String>()
    val visible = text.count { !it.isWhitespace() }
    if (visible < 100) warnings += "样本章节较短，请检查是否存在分页或内容缺失"
    val replacement = text.count { it == '\uFFFD' }
    val privateUse = text.count { it in '\uE000'..'\uF8FF' }
    if (replacement >= 3 && replacement.toDouble() / visible.coerceAtLeast(1) > 0.01) {
        warnings += "存在较多替换字符，可能是编码错误"
    }
    if (privateUse >= 5 && privateUse.toDouble() / visible.coerceAtLeast(1) > 0.02) {
        warnings += "存在字体私用区字符，可能需要字体解混淆"
    }
    if (visible < 2000 && listOf("人机验证", "访问验证", "Access Denied", "Just a moment...").any { text.contains(it, ignoreCase = true) }) {
        warnings += "可能抓取到了网站验证页，请检查书源登录或规则"
    }
    return warnings
}
