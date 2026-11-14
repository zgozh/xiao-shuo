package io.legado.app.ui.main.hub

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.cardview.widget.CardView
import io.legado.app.R
import io.legado.app.base.BaseFragment
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.SearchBook
import io.legado.app.data.entities.rule.ExploreKind
import io.legado.app.databinding.FragmentReadingHubBinding
import io.legado.app.help.glide.ImageLoader
import io.legado.app.help.source.exploreKinds
import io.legado.app.help.source.SuppressSourceNavigation
import io.legado.app.lib.theme.backgroundColor
import io.legado.app.model.webBook.WebBook
import io.legado.app.ui.book.explore.ExploreShowActivity
import io.legado.app.ui.book.info.BookInfoActivity
import io.legado.app.ui.main.MainFragmentInterface
import io.legado.app.utils.dpToPx
import io.legado.app.utils.startActivity
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

class PlazaFragment : BaseFragment(R.layout.fragment_reading_hub), MainFragmentInterface {
    override val position: Int? get() = arguments?.getInt("position")
    private val binding by viewBinding(FragmentReadingHubBinding::bind)
    private var sources = emptyList<BookSource>()
    private var source: BookSource? = null
    private var categories = emptyList<ExploreKind>()
    private var category: ExploreKind? = null
    private var books = emptyList<SearchBook>()
    private var loadJob: Job? = null

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        binding.titleBar.title = "发现广场"
        setSupportToolbar(binding.titleBar.toolbar)
    }

    override fun onResume() {
        super.onResume()
        if (books.isEmpty()) load() else render()
    }

    private fun load(selectedSource: BookSource? = source, selectedCategory: ExploreKind? = null) {
        loadJob?.cancel()
        loadJob = viewLifecycleOwner.lifecycleScope.launch {
            render("正在读取真实榜单…")
            try {
                val result = withContext(Dispatchers.IO + SuppressSourceNavigation) {
                    withTimeout(30000) {
                        sources = appDb.bookSourceDao.allEnabledExplore.filter { !it.exploreUrl.isNullOrBlank() }
                        val selected = sources.firstOrNull { it.bookSourceUrl == selectedSource?.bookSourceUrl }
                            ?: sources.firstOrNull()
                        source = selected
                        if (selected == null) return@withTimeout emptyList<SearchBook>()
                        categories = selected.exploreKinds().filter { it.type == ExploreKind.Type.url && !it.url.isNullOrBlank() }
                        category = selectedCategory?.let { previous -> categories.firstOrNull { it.url == previous.url } }
                            ?: categories.firstOrNull { it.title.contains(Regex("榜|热门|排行|推荐")) }
                            ?: categories.firstOrNull()
                        val url = category?.url ?: return@withTimeout emptyList<SearchBook>()
                        WebBook.exploreBookAwait(selected, url, 1).also {
                            appDb.searchBookDao.insert(*it.toTypedArray())
                        }
                    }
                }
                books = result
                render(if (books.isEmpty()) "当前分类未返回作品，可切换书源或分类。" else null)
            } catch (error: Exception) {
                if (error is CancellationException && error !is kotlinx.coroutines.TimeoutCancellationException) throw error
                books = emptyList()
                render("加载失败：${error.localizedMessage ?: "请求超时"}。可重试或切换来源。")
            }
        }
    }

    private fun render(message: String? = null) {
        binding.hubContent.apply {
            removeAllViews()
            heading("发现下一本好书")
            note("热门、推荐与分类榜单来自你启用的书源，排名和更新以来源页面为准。")
            action("书源：${source?.bookSourceName ?: "选择来源"}") {
                if (sources.isEmpty()) startActivity<SourceCatalogActivity>() else {
                    AlertDialog.Builder(requireContext()).setTitle("切换榜单来源")
                        .setItems(sources.map { it.bookSourceName }.toTypedArray()) { _, index ->
                            books = emptyList()
                            load(sources[index])
                        }.show()
                }
            }
            action("分类 / 榜单：${category?.title ?: "选择分类"}") {
                if (categories.isNotEmpty()) AlertDialog.Builder(requireContext()).setTitle("分类与排行榜")
                    .setItems(categories.map { it.title }.toTypedArray()) { _, index ->
                        books = emptyList()
                        load(source, categories[index])
                    }.show()
            }
            action("全部书源分类与发现") { startActivity<AllExploreActivity>() }
            if (message != null) note(message)
            books.take(20).forEachIndexed { index, book ->
                val card = CardView(context).apply {
                    radius = 14.dpToPx().toFloat()
                    setCardBackgroundColor(context.backgroundColor)
                    isClickable = true
                    isFocusable = true
                    setOnClickListener {
                        startActivity<BookInfoActivity> {
                            putExtra("name", book.name)
                            putExtra("author", book.author)
                            putExtra("bookUrl", book.bookUrl)
                        }
                    }
                }
                val row = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(12.dpToPx(), 12.dpToPx(), 12.dpToPx(), 12.dpToPx())
                }
                val cover = ImageView(context).apply {
                    contentDescription = "${book.name}的封面"
                    scaleType = ImageView.ScaleType.CENTER_CROP
                }
                row.addView(cover, LinearLayout.LayoutParams(66.dpToPx(), 94.dpToPx()).apply { marginEnd = 14.dpToPx() })
                ImageLoader.load(this@PlazaFragment, viewLifecycleOwner.lifecycle, book.coverUrl).into(cover)
                row.addView(LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    heading("${index + 1}. ${book.name}", 18f)
                    note("${book.author}\n${book.kind.orEmpty()}\n${book.latestChapterTitle.orEmpty()}")
                }, LinearLayout.LayoutParams(0, -2, 1f))
                card.addView(row)
                addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 12.dpToPx() })
            }
            action("刷新当前榜单") { load(source, category) }
            if (category?.url != null) action("查看完整列表与下一页") {
                startActivity<ExploreShowActivity> {
                    putExtra("sourceUrl", source?.bookSourceUrl)
                    putExtra("exploreName", category?.title)
                    putExtra("exploreUrl", category?.url)
                    putExtra("showExploreCategories", true)
                }
            }
            action("获取更多书源") { startActivity<SourceCatalogActivity>() }
        }
    }

    companion object {
        fun create(position: Int) = PlazaFragment().apply {
            arguments = Bundle().apply { putInt("position", position) }
        }
    }
}
