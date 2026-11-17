package io.legado.app.ui.main.hub

import android.content.Context
import android.util.AtomicFile
import androidx.annotation.Keep
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.SearchBook
import io.legado.app.data.entities.rule.ExploreKind
import io.legado.app.help.source.exploreKinds
import io.legado.app.help.book.bookIdentity
import io.legado.app.model.webBook.WebBook
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonArray
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

internal data class DiscoveryTab(val id: String, val title: String, val pattern: Regex)

internal val discoveryTabs = listOf(
    DiscoveryTab("hot", "热门榜", Regex("月票榜|总排行|总点击|日点击|热门|总榜")),
    DiscoveryTab("new", "新书与更新", Regex("新书榜|更新榜|最近更新|最新入库|最新小说")),
    DiscoveryTab("complete", "完结精选", Regex("畅销完本|完本排行|全本小说|完结精品|完本|完结")),
    DiscoveryTab("fantasy", "玄幻", Regex("玄幻")),
    DiscoveryTab("immortal", "仙侠", Regex("仙侠|修真")),
    DiscoveryTab("urban", "都市", Regex("都市")),
    DiscoveryTab("history", "历史", Regex("历史")),
    DiscoveryTab("scifi", "科幻", Regex("科幻")),
    DiscoveryTab("bestsell", "畅销榜", Regex("畅销榜|畅销|热销")),
    DiscoveryTab("votes", "推荐榜", Regex("推荐榜|总推荐|周推荐|推荐排行")),
    DiscoveryTab("daily", "日榜", Regex("日点击|日榜|日排行")),
    DiscoveryTab("weekly", "周榜", Regex("周点击|周榜|周排行")),
    DiscoveryTab("suspense", "悬疑", Regex("悬疑|灵异")),
    DiscoveryTab("game", "游戏", Regex("游戏|网游")),
    DiscoveryTab("romance", "言情", Regex("言情|女生|女频")),
    DiscoveryTab("japanese", "日本轻小说", Regex("最新|最高评分|最多观看|最多收藏")),
)

internal val discoveryRankIds = setOf("hot", "bestsell", "votes", "daily", "weekly")
internal val discoveryFeaturedIds = listOf("hot", "new", "complete", "fantasy", "urban", "immortal")

internal data class DiscoveryLoadResult(
    val section: DiscoverySection?,
    val attemptedSources: Int,
    val failure: String? = null,
)

internal data class DiscoverySectionState(
    val section: DiscoverySection? = null,
    val loading: Boolean = false,
    val fresh: Boolean = false,
    val message: String? = null,
) {
    fun begin() = copy(loading = true, message = null)
    fun complete(result: DiscoveryLoadResult): DiscoverySectionState =
        if (result.section != null) DiscoverySectionState(result.section, fresh = true)
        else copy(loading = false, fresh = false, message = result.failure ?: "暂未找到可用榜单")
}

internal fun discoveryBooks(books: List<SearchBook>) = books.filter {
    it.name.isNotBlank() && it.bookUrl.isNotBlank()
}.distinctBy { bookIdentity(it.name, it.author) }

/** A failed request never advances the page; page one replaces a cached preview. */
internal class DiscoveryPages(cached: List<SearchBook> = emptyList()) {
    val books = discoveryBooks(cached).toMutableList()
    var nextPage = 1
        private set
    var hasMore = true
        private set
    var cached = books.isNotEmpty()
        private set

    fun accept(page: Int, incoming: List<SearchBook>): Boolean {
        if (page != nextPage) return false
        val clean = discoveryBooks(incoming)
        if (page == 1) {
            if (clean.isEmpty()) return false
            books.clear()
            books.addAll(clean)
            cached = false
        } else {
            val keys = books.map { bookIdentity(it.name, it.author) }.toSet()
            val added = clean.filter { bookIdentity(it.name, it.author) !in keys }
            books.addAll(added)
            hasMore = added.isNotEmpty()
        }
        nextPage++
        return true
    }
}

@Keep
internal data class DiscoverySection(
    val id: String,
    val sourceUrl: String,
    val sourceName: String,
    val categoryTitle: String,
    val categoryUrl: String,
    val updatedAt: Long,
    val books: List<SearchBook>,
)

/** Empty pages and errors fall through to another enabled source. */
internal class DiscoveryLoader(
    private val kinds: suspend (BookSource) -> List<ExploreKind> = { it.exploreKinds() },
    private val fetch: suspend (BookSource, String) -> List<SearchBook> = { source, url ->
        ensureDiscoveryGuestSession(source.bookSourceUrl)
        WebBook.exploreBookAwait(source, url, 1)
    },
) {
    suspend fun load(
        tab: DiscoveryTab,
        sources: List<BookSource>,
        previous: DiscoverySection?,
        rotate: Boolean = false,
    ): DiscoveryLoadResult {
        val preferred = if (tab.id !in discoveryRankIds && tab.id != "complete") {
            listOf("https://www.sudugu.co", "https://m.qidian.com##部分限免源", "http://wap.80zw.la/", "https://www.shuhaige.net/")
        } else {
            listOf("https://m.qidian.com##部分限免源", "https://www.sudugu.co", "http://wap.80zw.la/", "https://www.shuhaige.net/")
        }
        val ordered = sources.filter { it.enabled && it.enabledExplore &&
            (it.bookSourceType == 0 || (tab.id == "japanese" && it.bookSourceType == 3)) }
            .filter { tab.id != "japanese" || it.hasGroup("日本轻小说") }
            .sortedBy { source ->
                if (source.bookSourceUrl == previous?.sourceUrl) {
                    if (rotate) Int.MAX_VALUE else -1
                }
                else preferred.indexOf(source.bookSourceUrl).takeIf { it >= 0 } ?: preferred.size
            }
        var attempted = 0
        var lastFailure: String? = null
        val section = withTimeoutOrNull(25000) overall@{
            for (source in ordered.take(12)) {
                attempted++
                val result = try {
                    withTimeoutOrNull(6000) candidate@{
                        val categories = kinds(source).filter {
                            it.type == ExploreKind.Type.url && !it.url.isNullOrBlank() &&
                                !it.title.startsWith("ERROR:") && tab.pattern.containsMatchIn(it.title)
                                && (tab.id != "japanese" || source.bookSourceUrl != "https://www.esjzone.one" ||
                                it.url.orEmpty().startsWith("/list-1"))
                        }.distinctBy { it.url }.take(3)
                        for (category in categories) {
                            val books = discoveryBooks(fetch(source, category.url!!))
                            if (books.isNotEmpty()) return@candidate DiscoverySection(
                                tab.id, source.bookSourceUrl, source.bookSourceName,
                                category.title, category.url, System.currentTimeMillis(), books.take(60),
                            )
                        }
                        null
                    }
                } catch (error: Exception) {
                    if (error is CancellationException) throw error
                    lastFailure = error.localizedMessage
                    null
                }
                if (result != null) return@overall result
            }
            null
        }
        return DiscoveryLoadResult(section, attempted, when {
            section != null -> null
            ordered.isEmpty() -> "没有启用的发现书源，可前往书源管理开启"
            lastFailure != null -> "已尝试 $attempted 个来源，暂时无法更新，请稍后重试"
            else -> "已尝试 $attempted 个来源，暂未返回该榜单作品"
        })
    }
}

internal class DiscoveryCache(private val context: Context) {
    private val file get() = AtomicFile(File(context.filesDir, "goubin-discovery.json"))

    fun read(): List<DiscoverySection> {
        val snapshot = runCatching {
            GSON.fromJsonArray<DiscoverySection>(file.openRead().bufferedReader().use { it.readText() }).getOrThrow()
        }.getOrDefault(emptyList())
        val bundled = runCatching {
            context.assets.open("defaultData/goubinDiscovery.json").bufferedReader().use {
                GSON.fromJsonArray<DiscoverySection>(it.readText()).getOrThrow()
            }
        }.getOrDefault(emptyList())
        return (snapshot + bundled).filter { it.books.isNotEmpty() }.distinctBy { it.id }
    }

    fun write(sections: List<DiscoverySection>) {
        val target = file
        val stream = target.startWrite()
        try {
            stream.write(GSON.toJson(sections).toByteArray(Charsets.UTF_8))
            target.finishWrite(stream)
        } catch (error: Exception) {
            target.failWrite(stream)
            throw error
        }
    }
}
