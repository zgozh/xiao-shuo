package io.legado.app.ui.main.hub

import com.google.gson.JsonParser
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

internal data class SourceRepository(val name: String, val url: String, val description: String)

internal fun parseCatalog(json: String): List<SourceRepository> {
    val root = JsonParser.parseString(json).asJsonObject
    require(root.get("version").asInt == 1) { "不支持的目录版本" }
    val entries = root.getAsJsonArray("repositories")
    require(entries.size() in 1..100) { "书源目录为空或条目过多" }
    return entries.map { item ->
        val entry = item.asJsonObject
        val name = entry.get("name").asString.trim()
        val url = entry.get("url").asString.trim()
        require(name.isNotEmpty() && url.toHttpUrlOrNull()?.isHttps == true) {
            "书源目录中的名称或地址无效"
        }
        SourceRepository(name, url, entry.get("description")?.asString.orEmpty())
    }.distinctBy { it.url }
}
