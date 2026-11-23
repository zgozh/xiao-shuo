package io.legado.app.ui.main.hub

import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.SearchBook
import io.legado.app.data.entities.rule.ExploreKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class DiscoveryLoaderTest {
    @Test fun japaneseTabOnlyUsesItsGroupAndJapaneseCategories() = runBlocking {
        val visited = arrayListOf<String>()
        val loader = DiscoveryLoader({ listOf(
            ExploreKind("最高评分", "/list-03/1.html"),
            ExploreKind("最高评分", "/list-13/1.html"),
        ) }) { source, url ->
            visited += "${source.bookSourceUrl}$url"
            listOf(SearchBook(bookUrl = "a", name = "译作"))
        }
        val result = loader.load(discoveryTabs.first { it.id == "japanese" }, listOf(
            BookSource(bookSourceUrl = "chinese"),
            BookSource(bookSourceUrl = "https://www.esjzone.one", bookSourceGroup = "日本轻小说"),
        ), null)
        assertEquals("/list-13/1.html", result.section!!.categoryUrl)
        assertEquals(listOf("https://www.esjzone.one/list-13/1.html"), visited)
    }

    private val sources = listOf("empty", "broken", "healthy").map {
        BookSource(bookSourceUrl = it, bookSourceName = it)
    }
    private val kinds = listOf(ExploreKind("月票榜", "/rank"))

    @Test fun fallsBackAfterEmptyAndBrokenSourcesAndDeduplicatesBooks() = runBlocking {
        val visited = arrayListOf<String>()
        val loader = DiscoveryLoader({ kinds }) { source, _ ->
            visited += source.bookSourceUrl
            when (source.bookSourceUrl) {
                "empty" -> emptyList()
                "broken" -> error("offline")
                else -> listOf(
                    SearchBook(bookUrl = "a", name = "作品", author = "作者"),
                    SearchBook(bookUrl = "b", name = "作品", author = "作者"),
                    SearchBook(bookUrl = "c", name = ""),
                )
            }
        }
        val result = loader.load(discoveryTabs[0], sources, null)
        val section = result.section!!
        assertEquals(listOf("empty", "broken", "healthy"), visited)
        assertEquals("healthy", section.sourceUrl)
        assertEquals(listOf("a"), section.books.map { it.bookUrl })
        assertEquals(3, result.attemptedSources)
    }

    @Test fun disabledSourcesAndUnrelatedCategoriesAreNeverFetched() = runBlocking {
        val loader = DiscoveryLoader({ kinds }) { _, _ -> fail("must not fetch"); emptyList() }
        assertNull(loader.load(discoveryTabs[0], sources.map { it.copy(enabled = false) }, null).section)
        assertNull(loader.load(discoveryTabs[2], sources, null).section)
    }

    @Test fun allEmptyReturnsNoReplacementForExistingCache() = runBlocking {
        val loader = DiscoveryLoader({ kinds }) { _, _ -> emptyList() }
        assertNull(loader.load(discoveryTabs[0], sources, null).section)
    }

    @Test fun cancellationStopsFallback() = runBlocking {
        val loader = DiscoveryLoader({ kinds }) { _, _ -> throw CancellationException("leaving page") }
        try {
            loader.load(discoveryTabs[0], sources, null)
            fail("cancellation must propagate")
        } catch (_: CancellationException) { }
    }

    @Test fun triesAnotherMatchingCategoryBeforeDiscardingSource() = runBlocking {
        val loader = DiscoveryLoader({ listOf(ExploreKind("月票榜", "/empty"), ExploreKind("总排行榜", "/full")) }) { _, url ->
            if (url == "/empty") emptyList() else listOf(SearchBook(bookUrl = "a", name = "作品"))
        }
        assertEquals("/full", loader.load(discoveryTabs[0], sources, null).section!!.categoryUrl)
    }

    @Test fun rotatePrefersADifferentEnabledSource() = runBlocking {
        val previous = DiscoverySection("hot", "empty", "first", "月票榜", "/rank", 1, emptyList())
        val loader = DiscoveryLoader({ kinds }) { _, _ -> listOf(SearchBook(bookUrl = "a", name = "作品")) }
        assertEquals("empty", loader.load(discoveryTabs[0], sources, previous).section!!.sourceUrl)
        assertEquals("broken", loader.load(discoveryTabs[0], sources, previous, rotate = true).section!!.sourceUrl)
    }
}
