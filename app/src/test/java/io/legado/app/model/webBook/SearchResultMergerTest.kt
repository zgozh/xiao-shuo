package io.legado.app.model.webBook

import io.legado.app.data.entities.SearchBook
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class SearchResultMergerTest {
    private fun book(name: String, author: String, source: String, kind: String? = null) =
        SearchBook(bookUrl = "https://$source/book", name = name, author = author, origin = source, kind = kind)

    @Test fun titleWrappersWhitespaceAndAuthorLabelsMergeAcrossSources() = runBlocking {
        val results = mergeSearchResults(emptyList(), listOf(
            book("执魔", "我是墨水", "a"),
            book("《执 魔》", "作者：我是墨水", "b"),
            book("执魔（完结）", "我是墨水 著", "c"),
            book("\u200b《执魔(完结)》", "我是墨水", "d"),
        ), "执魔", true)
        assertEquals(1, results.size)
        assertEquals(setOf("a", "b", "c", "d"), results.single().origins)
    }

    @Test fun sameWorkCannotBeSplitByGenreRankingBuckets() = runBlocking {
        val first = book("玄幻之旅", "甲", "a", "玄幻")
        val second = book("玄幻之旅", "甲", "b", "仙侠")
        val results = mergeSearchResults(listOf(first), listOf(second), "玄幻", true)
        assertEquals(1, results.size)
        assertEquals(2, results.single().origins.size)
    }

    @Test fun differentAuthorsAndVolumesStaySeparate() = runBlocking {
        val results = mergeSearchResults(emptyList(), listOf(
            book("九龙至尊", "盘古", "a"), book("九龙至尊", "小葱点豆腐", "b"),
            book("九龙至尊（二）", "盘古", "c"), book("九龙至尊（上）", "盘古", "d"),
        ), "九龙至尊", true)
        assertEquals(4, results.size)
    }

    @Test fun missingAuthorDoesNotGetAssignedToAKnownAuthor() = runBlocking {
        val results = mergeSearchResults(emptyList(), listOf(
            book("同名小说", "甲", "a"), book("同名小说", "乙", "b"), book("同名小说", "未知", "c"),
        ), "同名小说", true)
        assertEquals(3, results.size)
    }

    @Test fun repeatedSourceAndLaterPagesKeepAnAccurateSourceCount() = runBlocking {
        val first = mergeSearchResults(emptyList(), listOf(book("万古神帝", "飞天鱼", "a")), "万古神帝", true)
        val next = mergeSearchResults(first, listOf(
            book("万古 神帝", "飞天鱼", "a"), book("《万古神帝》", "飞天鱼", "b"),
        ), "万古神帝", true)
        assertEquals(1, next.size)
        assertEquals(2, next.single().origins.size)
    }

    @Test fun authorSearchRetainsDifferentWorksAndPrecisionFiltersUnrelatedHits() = runBlocking {
        val results = mergeSearchResults(emptyList(), listOf(
            book("执魔", "我是墨水", "a"), book("合体双修", "作者：我是墨水", "b"),
            book("无关作品", "其他作者", "c"),
        ), "我是墨水", true)
        assertEquals(setOf("执魔", "合体双修"), results.map { it.name }.toSet())
    }

    @Test fun exactMatchesAndMoreSourcesComeFirst() = runBlocking {
        val results = mergeSearchResults(emptyList(), listOf(
            book("执魔外传", "甲", "a"), book("执魔", "我是墨水", "b"), book("执魔", "我是墨水", "c"),
            book("执魔", "乙", "d"),
        ), "执魔", true)
        assertEquals(listOf("我是墨水", "乙", "甲"), results.map { it.author })
    }
}
