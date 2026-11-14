package io.legado.app.ui.book.search

import android.content.Context
import android.os.Bundle
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import io.legado.app.R
import io.legado.app.base.adapter.DiffRecyclerAdapter
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.data.entities.SearchBook
import io.legado.app.databinding.ItemSearchBinding
import io.legado.app.help.config.AppConfig
import io.legado.app.utils.gone
import io.legado.app.utils.visible


class SearchAdapter(context: Context, val callBack: CallBack) :
    DiffRecyclerAdapter<SearchBook, ItemSearchBinding>(context) {

    override val keepScrollPosition = true

    override val diffItemCallback: DiffUtil.ItemCallback<SearchBook>
        get() = SearchResultDiffCallback()

    override fun getViewBinding(parent: ViewGroup): ItemSearchBinding {
        return ItemSearchBinding.inflate(inflater, parent, false)
    }

    override fun convert(
        holder: ItemViewHolder,
        binding: ItemSearchBinding,
        item: SearchBook,
        payloads: MutableList<Any>
    ) {
        if (payloads.isEmpty()) {
            bind(binding, item)
        } else {
            for (i in payloads.indices) {
                val bundle = payloads[i] as Bundle
                bindChange(binding, item, bundle)
            }
        }
    }

    override fun registerListener(holder: ItemViewHolder, binding: ItemSearchBinding) {
        binding.root.setOnClickListener {
            getItem(holder.layoutPosition)?.let {
                callBack.showBookInfo(it.name, it.author, it.bookUrl)
            }
        }
    }

    private fun bind(binding: ItemSearchBinding, searchBook: SearchBook) {
        binding.run {
            tvName.text = searchBook.name
            tvAuthor.text = context.getString(R.string.author_show, searchBook.author)
            upIndicator(binding, searchBook)
            bvOriginCount.setBadgeCount(searchBook.origins.size)
            upLasted(binding, searchBook.latestChapterTitle)
            tvIntroduce.text = searchBook.trimIntro(context)
            upKind(binding, searchBook.getKindList())
            ivCover.load(
                searchBook,
                AppConfig.loadCoverOnlyWifi
            )
        }
    }

    private fun bindChange(binding: ItemSearchBinding, searchBook: SearchBook, bundle: Bundle) {
        binding.run {
            bundle.keySet().forEach {
                when (it) {
                    "origins" -> bvOriginCount.setBadgeCount(searchBook.origins.size)
                    "last" -> upLasted(binding, searchBook.latestChapterTitle)
                    "intro" -> tvIntroduce.text = searchBook.trimIntro(context)
                    "kind" -> upKind(binding, searchBook.getKindList())
                    "isInBookshelf", "hasReadRecord" -> upIndicator(binding, searchBook)
                    "cover" -> ivCover.load(
                        searchBook,
                        false
                    )
                }
            }
        }
    }

    /**
     * 在书架中显示绿点,不在书架但有阅读记录显示橙点
     */
    private fun upIndicator(binding: ItemSearchBinding, searchBook: SearchBook) {
        val isInBookshelf = callBack.isInBookshelf(searchBook)
        binding.ivInBookshelf.isVisible = isInBookshelf
        binding.ivReadRecord.isVisible = !isInBookshelf && callBack.hasReadRecord(searchBook)
    }

    private fun upLasted(binding: ItemSearchBinding, latestChapterTitle: String?) {
        binding.run {
            if (latestChapterTitle.isNullOrEmpty()) {
                tvLasted.gone()
            } else {
                tvLasted.text =
                    context.getString(R.string.lasted_show, latestChapterTitle)
                tvLasted.visible()
            }
        }
    }

    private fun upKind(binding: ItemSearchBinding, kinds: List<String>) = binding.run {
        if (kinds.isEmpty()) {
            llKind.gone()
        } else {
            llKind.visible()
            llKind.setLabels(kinds)
        }
    }

    interface CallBack {

        /**
         * 是否已经加入书架
         */
        fun isInBookshelf(book: SearchBook): Boolean

        /**
         * 是否有阅读记录
         */
        fun hasReadRecord(book: SearchBook): Boolean

        /**
         * 显示书籍详情
         */
        fun showBookInfo(name: String, author: String, bookUrl: String)
    }
}
