package io.legado.app.model.webBook

import io.legado.app.data.entities.SearchBook
import io.legado.app.help.book.BookIdentity
import io.legado.app.help.book.bookIdentity
import io.legado.app.help.book.canonicalBookAuthor
import io.legado.app.help.book.canonicalBookName
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal fun searchResultRelevance(name: String, author: String, kind: String?, query: String): Int {
    val titleKey = canonicalBookName(query)
    val authorKey = canonicalBookAuthor(query)
    val identity = bookIdentity(name, author)
    return when {
        titleKey == identity.name || (authorKey.isNotEmpty() && authorKey == identity.author) -> 0
        kind?.contains(query, ignoreCase = true) == true -> 1
        identity.name.contains(titleKey) || (authorKey.isNotEmpty() && identity.author.contains(authorKey)) -> 2
        else -> 3
    }
}

/** Merge globally before ranking, so genre metadata cannot split the same work into two cards. */
internal suspend fun mergeSearchResults(
    existing: List<SearchBook>, incoming: List<SearchBook>, query: String, precision: Boolean,
): List<SearchBook> {
    val grouped = linkedMapOf<BookIdentity, SearchBook>()
    for (book in existing + incoming) {
        currentCoroutineContext().ensureActive()
        if (book.name.isBlank() || book.bookUrl.isBlank()) continue
        val identity = bookIdentity(book.name, book.author)
        if (precision && searchResultRelevance(book.name, book.author, book.kind, query) == 3) continue
        val representative = grouped[identity]
        if (representative == null) grouped[identity] = book
        else book.origins.forEach(representative::addOrigin)
    }
    return grouped.values.sortedWith(compareBy<SearchBook> {
        searchResultRelevance(it.name, it.author, it.kind, query)
    }.thenByDescending { it.origins.size })
}
