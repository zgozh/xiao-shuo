package io.legado.app.ui.main.hub

import android.os.Bundle
import android.view.Menu
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.legado.app.base.BaseActivity
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.SearchBook
import io.legado.app.data.entities.rule.ExploreKind
import io.legado.app.databinding.ActivityDiscoveryListBinding
import io.legado.app.help.source.SuppressSourceNavigation
import io.legado.app.help.source.exploreKinds
import io.legado.app.lib.theme.secondaryTextColor
import io.legado.app.model.webBook.WebBook
import io.legado.app.ui.book.info.BookInfoActivity
import io.legado.app.utils.dpToPx
import io.legado.app.utils.startActivity
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

class DiscoveryListActivity : BaseActivity<ActivityDiscoveryListBinding>() {
    override val binding by viewBinding(ActivityDiscoveryListBinding::inflate)
    private var pages = DiscoveryPages()
    private var sourceUrl = ""
    private var sourceName = ""
    private var categoryUrl = ""
    private var categoryTitle = ""
    private var sectionId = ""
    private var request: Job? = null
    private var selecting: Job? = null
    private val bookAdapter = object : RecyclerView.Adapter<BookHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            BookHolder(DiscoveryBookView(parent.context).apply {
                layoutParams = RecyclerView.LayoutParams(-1, -2).apply {
                    setMargins(6.dpToPx(), 0, 6.dpToPx(), 16.dpToPx())
                }
            })
        override fun getItemCount() = pages.books.size
        override fun onBindViewHolder(holder: BookHolder, position: Int) {
            val book = pages.books[position]
            holder.tile.bind(book, position + 1) { openBook(book) }
        }
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        fun value(key: String) = savedInstanceState?.getString(key) ?: intent.getStringExtra(key).orEmpty()
        sourceUrl = value("sourceUrl")
        sourceName = value("sourceName")
        categoryUrl = value("exploreUrl")
        categoryTitle = value("exploreName")
        sectionId = value("sectionId")
        binding.titleBar.title = categoryTitle
        binding.status.setTextColor(secondaryTextColor)
        binding.books.layoutManager = GridLayoutManager(this, 3)
        binding.books.adapter = bookAdapter
        binding.categories.setOnClickListener { chooseCategories() }
        binding.sources.setOnClickListener { chooseSource() }
        binding.nextPage.setOnClickListener { loadPage() }
        showCachedAndLoad()
    }

    override fun onCompatCreateOptionsMenu(menu: Menu): Boolean {
        menu.add("刷新当前榜单").setOnMenuItemClickListener { showCachedAndLoad(); true }
        menu.add("尝试其他来源").setOnMenuItemClickListener { tryOtherSource(); true }
        return true
    }

    override fun onSaveInstanceState(outState: Bundle) {
        mapOf("sourceUrl" to sourceUrl, "sourceName" to sourceName, "exploreUrl" to categoryUrl,
            "exploreName" to categoryTitle, "sectionId" to sectionId).forEach { (key, value) -> outState.putString(key, value) }
        super.onSaveInstanceState(outState)
    }

    private fun showCachedAndLoad() {
        request?.cancel()
        request = lifecycleScope.launch {
            val cache = withContext(Dispatchers.IO) {
                val enabled = appDb.bookSourceDao.getBookSource(sourceUrl)?.let { it.enabled && it.enabledExplore } == true
                if (enabled) DiscoveryCache(applicationContext).read().firstOrNull {
                    it.sourceUrl == sourceUrl && it.categoryUrl == categoryUrl
                } else null
            }
            pages = DiscoveryPages(cache?.books.orEmpty())
            bookAdapter.notifyDataSetChanged()
            binding.books.scrollToPosition(0)
            loadPageNow()
        }
    }

    private fun loadPage() {
        if (request?.isActive == true || !pages.hasMore) return
        request = lifecycleScope.launch { loadPageNow() }
    }

    private suspend fun loadPageNow() {
        val state = pages
        val page = state.nextPage
        binding.nextPage.isEnabled = false
        binding.nextPage.text = "正在加载第 $page 页…"
        binding.status.text = "$sourceName · ${if (state.cached) "缓存 ${state.books.size} 本，正在刷新" else "已加载 ${state.books.size} 本"}"
        try {
            val loaded = withContext(Dispatchers.IO + SuppressSourceNavigation) {
                withTimeout(15000) {
                    val source = appDb.bookSourceDao.getBookSource(sourceUrl) ?: error("书源已移除")
                    check(source.enabled && source.enabledExplore) { "该书源已停用" }
                    ensureDiscoveryGuestSession(source.bookSourceUrl)
                    discoveryBooks(WebBook.exploreBookAwait(source, categoryUrl, page)).also {
                        if (it.isNotEmpty()) appDb.searchBookDao.insert(*it.toTypedArray())
                    }
                }
            }
            if (!state.accept(page, loaded)) error("当前榜单没有返回作品")
            bookAdapter.notifyDataSetChanged()
            binding.status.text = "$sourceName · 已加载 ${state.nextPage - 1} 页 / ${state.books.size} 本"
            binding.nextPage.text = if (state.hasMore) "加载下一页" else "没有更多作品"
        } catch (error: Exception) {
            if (error is CancellationException && error !is kotlinx.coroutines.TimeoutCancellationException) throw error
            binding.status.text = "$sourceName · ${error.localizedMessage ?: "请求失败"}\n已保留 ${state.books.size} 本，可重试或切换书源。"
            binding.nextPage.text = "重试第 $page 页"
        } finally {
            binding.nextPage.isEnabled = state.hasMore
        }
    }

    private fun chooseSource() {
        selecting?.cancel()
        selecting = lifecycleScope.launch {
            val sources = withContext(Dispatchers.IO) {
                appDb.bookSourceDao.allEnabledExplore.filter {
                    it.enabled && it.bookSourceType == 0 && !it.exploreUrl.isNullOrBlank()
                }.sortedBy { if (it.bookSourceUrl == sourceUrl) 0 else 1 }
            }
            if (sources.isEmpty()) { toastOnUi("没有可用的发现书源"); return@launch }
            AlertDialog.Builder(this@DiscoveryListActivity).setTitle("选择榜单书源")
                .setItems(sources.map { it.bookSourceName }.toTypedArray()) { _, index -> chooseCategories(sources[index]) }
                .setNegativeButton("取消", null).show()
        }
    }

    private fun chooseCategories(selectedSource: BookSource? = null) {
        selecting?.cancel()
        selecting = lifecycleScope.launch {
            try {
                val (source, kinds) = withContext(Dispatchers.IO + SuppressSourceNavigation) {
                    withTimeout(8000) {
                        val source = selectedSource ?: appDb.bookSourceDao.getBookSource(sourceUrl) ?: error("书源已移除")
                        source to source.exploreKinds().filter {
                            it.type == ExploreKind.Type.url && !it.url.isNullOrBlank() && !it.title.startsWith("ERROR:")
                        }.distinctBy { it.title.trim() }
                    }
                }
                if (kinds.isEmpty()) { toastOnUi("该书源暂未提供可用分类"); return@launch }
                AlertDialog.Builder(this@DiscoveryListActivity).setTitle("${source.bookSourceName} · 榜单与分类")
                    .setItems(kinds.map { it.title }.toTypedArray()) { _, index ->
                        val category = kinds[index]
                        sourceUrl = source.bookSourceUrl
                        sourceName = source.bookSourceName
                        categoryUrl = category.url!!
                        categoryTitle = category.title
                        sectionId = discoveryTabs.firstOrNull { it.pattern.containsMatchIn(category.title) }?.id.orEmpty()
                        binding.titleBar.title = categoryTitle
                        showCachedAndLoad()
                    }.setNegativeButton("取消", null).show()
            } catch (error: Exception) {
                if (error is CancellationException && error !is kotlinx.coroutines.TimeoutCancellationException) throw error
                toastOnUi("分类读取失败，请切换其他书源")
            }
        }
    }

    private fun tryOtherSource() {
        val tab = discoveryTabs.firstOrNull { it.id == sectionId }
        if (tab == null) { chooseSource(); return }
        request?.cancel()
        request = lifecycleScope.launch {
            binding.nextPage.isEnabled = false
            binding.status.text = "正在寻找其他可用来源，已有作品会保留…"
            val previous = DiscoverySection(sectionId, sourceUrl, sourceName, categoryTitle, categoryUrl, 0, pages.books.toList())
            val result = withContext(Dispatchers.IO + SuppressSourceNavigation) {
                DiscoveryLoader().load(tab, appDb.bookSourceDao.allEnabledExplore, previous, rotate = true)
            }
            val section = result.section
            if (section == null) {
                binding.status.text = result.failure
            } else {
                sourceUrl = section.sourceUrl
                sourceName = section.sourceName
                categoryUrl = section.categoryUrl
                categoryTitle = section.categoryTitle
                pages = DiscoveryPages().apply { accept(1, section.books) }
                binding.titleBar.title = categoryTitle
                bookAdapter.notifyDataSetChanged()
                binding.books.scrollToPosition(0)
                binding.status.text = "$sourceName · 已加载 1 页 / ${pages.books.size} 本"
            }
            binding.nextPage.text = if (pages.hasMore) "加载下一页" else "没有更多作品"
            binding.nextPage.isEnabled = pages.hasMore
        }
    }

    private fun openBook(book: SearchBook) = lifecycleScope.launch {
        withContext(Dispatchers.IO) { appDb.searchBookDao.insert(book) }
        startActivity<BookInfoActivity> {
            putExtra("name", book.name)
            putExtra("author", book.author)
            putExtra("bookUrl", book.bookUrl)
        }
    }

    private class BookHolder(val tile: DiscoveryBookView) : RecyclerView.ViewHolder(tile)
}
