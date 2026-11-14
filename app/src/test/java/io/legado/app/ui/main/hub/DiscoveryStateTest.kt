package io.legado.app.ui.main.hub

import io.legado.app.data.entities.SearchBook
import org.junit.Assert.*
import org.junit.Test

class DiscoveryStateTest {
    private fun book(name: String, url: String = name) = SearchBook(bookUrl = url, name = name, author = "作者")
    private val cached = DiscoverySection("hot", "source", "来源", "月票榜", "/rank", 10, listOf(book("旧书")))

    @Test fun failedRefreshPreservesBooksAndMarksThemCached() {
        val pending = DiscoverySectionState(cached, fresh = true).begin()
        assertTrue(pending.loading)
        val failed = pending.complete(DiscoveryLoadResult(null, 3, "网络不可用"))
        assertSame(cached, failed.section)
        assertFalse(failed.loading)
        assertFalse(failed.fresh)
        assertEquals("网络不可用", failed.message)
    }

    @Test fun successfulRefreshReplacesOldSectionAndClearsError() {
        val fresh = cached.copy(updatedAt = 20, books = listOf(book("新书")))
        val result = DiscoverySectionState(cached, message = "上次失败").begin()
            .complete(DiscoveryLoadResult(fresh, 1))
        assertSame(fresh, result.section)
        assertTrue(result.fresh)
        assertFalse(result.loading)
        assertNull(result.message)
    }

    @Test fun sectionsCanFinishIndependently() {
        val hot = DiscoverySectionState(cached).begin()
        val complete = DiscoverySectionState().begin()
        assertFalse(hot.complete(DiscoveryLoadResult(cached, 1)).loading)
        assertTrue(complete.loading)
        assertNull(complete.section)
    }

    @Test fun firstPageReplacesCachedPreviewAndNextPageDeduplicatesByWork() {
        val state = DiscoveryPages(listOf(book("旧书")))
        assertTrue(state.cached)
        assertTrue(state.accept(1, listOf(book("甲"), book("乙"))))
        assertEquals(listOf("甲", "乙"), state.books.map { it.name })
        assertFalse(state.cached)
        assertEquals(2, state.nextPage)
        assertTrue(state.accept(2, listOf(book("乙", "another-url"), book("丙"))))
        assertEquals(listOf("甲", "乙", "丙"), state.books.map { it.name })
        assertEquals(3, state.nextPage)
    }

    @Test fun emptyFirstPagePreservesPreviewAndRetryPage() {
        val state = DiscoveryPages(listOf(book("旧书")))
        assertFalse(state.accept(1, emptyList()))
        assertEquals(listOf("旧书"), state.books.map { it.name })
        assertEquals(1, state.nextPage)
        assertTrue(state.hasMore)
        assertTrue(state.cached)
    }

    @Test fun repeatedOrEmptyLaterPageEndsPagination() {
        listOf(listOf(book("甲", "changed-url")), emptyList()).forEach { incoming ->
            val state = DiscoveryPages()
            state.accept(1, listOf(book("甲")))
            assertTrue(state.accept(2, incoming))
            assertFalse(state.hasMore)
            assertEquals(1, state.books.size)
        }
    }

    @Test fun oldOrOutOfOrderResponsesCannotChangeCurrentPage() {
        val state = DiscoveryPages()
        assertFalse(state.accept(2, listOf(book("过早"))))
        state.accept(1, listOf(book("正确")))
        assertFalse(state.accept(1, listOf(book("过期"))))
        assertEquals(listOf("正确"), state.books.map { it.name })
        assertEquals(2, state.nextPage)
    }
}
