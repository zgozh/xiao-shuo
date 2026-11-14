package io.legado.app.ui.book.search

import io.legado.app.data.entities.SearchBook
import org.junit.Assert.*
import org.junit.Test

class SearchResultDiffCallbackTest {
    private val callback = SearchResultDiffCallback()

    @Test fun keepsTheSameRowWhenSourceMetadataChanges() {
        val old = SearchBook(bookUrl = "https://example.com/1", name = "《万古神帝》", author = "作者：飞天鱼")
        val updated = old.copy(name = "万古神帝", author = "飞天鱼")
        assertTrue(callback.areItemsTheSame(old, updated))
    }

    @Test fun differentRepresentativeAddressesAreDifferentRows() {
        val old = SearchBook(bookUrl = "https://example.com/1", name = "万古神帝", author = "飞天鱼")
        assertFalse(callback.areItemsTheSame(old, old.copy(bookUrl = "https://other.example/2")))
    }
}
