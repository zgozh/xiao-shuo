package io.legado.app.ui.book.search

import android.os.Bundle
import androidx.recyclerview.widget.DiffUtil
import io.legado.app.data.entities.SearchBook

/** The merger retains each work's first representative URL throughout a search. */
internal class SearchResultDiffCallback : DiffUtil.ItemCallback<SearchBook>() {
    override fun areItemsTheSame(oldItem: SearchBook, newItem: SearchBook): Boolean =
        oldItem.bookUrl == newItem.bookUrl

    override fun areContentsTheSame(oldItem: SearchBook, newItem: SearchBook): Boolean = false

    override fun getChangePayload(oldItem: SearchBook, newItem: SearchBook): Any = Bundle().apply {
        putInt("origins", newItem.origins.size)
        if (oldItem.coverUrl != newItem.coverUrl) putString("cover", newItem.coverUrl)
        if (oldItem.kind != newItem.kind) putString("kind", newItem.kind)
        if (oldItem.latestChapterTitle != newItem.latestChapterTitle) putString("last", newItem.latestChapterTitle)
        if (oldItem.intro != newItem.intro) putString("intro", newItem.intro)
    }
}
