package io.legado.app.help.book

import java.text.Normalizer

internal data class BookIdentity(val name: String, val author: String)

private val spacing = Regex("[\\s\\p{Z}\\p{Cf}]+")
private val authorPrefix = Regex("^(?:作\\s*者|著者)\\s*[:：]\\s*")
private val authorSuffix = Regex("(?:\\s+著|[（(](?:著|作者)[）)])\\s*$")
private val editionStatus = Regex("\\s*[（(【\\[]\\s*(?:完本|完结|连载中?|全文阅读|最新章节|无弹窗)\\s*[）)】\\]]\\s*$")
private val unknownAuthors = setOf("", "佚名", "未知", "作者未知", "暂无", "不详")
private val formatting = Regex("\\p{Cf}")

private fun normalized(value: String) = Normalizer.normalize(value, Normalizer.Form.NFKC).replace(formatting, "").trim()

internal fun canonicalBookName(value: String): String {
    var name = normalized(value)
    repeat(2) {
        name = name.replace(editionStatus, "").trim()
        if (name.startsWith("《") && name.endsWith("》")) name = name.substring(1, name.length - 1)
    }
    return name.replace(spacing, "").lowercase(java.util.Locale.ROOT)
}

internal fun canonicalBookAuthor(value: String): String {
    val author = normalized(value).replace(authorPrefix, "").replace(authorSuffix, "")
        .replace(spacing, "").lowercase(java.util.Locale.ROOT)
    return if (author in unknownAuthors) "" else author
}

internal fun bookIdentity(name: String, author: String) = BookIdentity(canonicalBookName(name), canonicalBookAuthor(author))
