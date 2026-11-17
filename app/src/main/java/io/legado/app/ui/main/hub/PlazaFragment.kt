package io.legado.app.ui.main.hub

import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.base.BaseFragment
import io.legado.app.constant.AppLog
import io.legado.app.data.appDb
import io.legado.app.data.entities.SearchBook
import io.legado.app.databinding.FragmentDiscoveryBinding
import io.legado.app.help.source.SuppressSourceNavigation
import io.legado.app.lib.theme.primaryColor
import io.legado.app.lib.theme.primaryTextColor
import io.legado.app.lib.theme.secondaryTextColor
import io.legado.app.ui.book.info.BookInfoActivity
import io.legado.app.ui.book.source.manage.BookSourceActivity
import io.legado.app.ui.main.MainFragmentInterface
import io.legado.app.utils.dpToPx
import io.legado.app.utils.startActivity
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PlazaFragment : BaseFragment(R.layout.fragment_discovery), MainFragmentInterface {
    override val position: Int? get() = arguments?.getInt("position")
    private val binding by viewBinding(FragmentDiscoveryBinding::bind)
    private val states = linkedMapOf<String, DiscoverySectionState>()
    private val panels = linkedMapOf<String, LinearLayout>()
    private var selected = "featured"
    private var rank = "hot"
    private var loadJob: Job? = null
    private var initialized = false

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        binding.titleBar.title = "发现"
        setSupportToolbar(binding.titleBar.toolbar)
        selected = savedInstanceState?.getString("discoveryTab") ?: selected
        rank = savedInstanceState?.getString("discoveryRank") ?: rank
        initialized = false
    }

    override fun onResume() {
        super.onResume()
        if (!initialized) {
            initialized = true
            buildContent()
            refresh()
        } else {
            viewLifecycleOwner.lifecycleScope.launch {
                val enabled = withContext(Dispatchers.IO) {
                    appDb.bookSourceDao.allEnabledExplore.filter { it.enabled && (it.bookSourceType == 0 || it.bookSourceType == 3) }
                        .map { it.bookSourceUrl }.toSet()
                }
                states.keys.toList().forEach { id ->
                    if (states[id]?.section?.sourceUrl?.let { it !in enabled } == true) {
                        states[id] = DiscoverySectionState(message = "原书源已停用，请刷新尝试其他来源")
                        renderSection(id)
                    }
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("discoveryTab", selected)
        outState.putString("discoveryRank", rank)
        super.onSaveInstanceState(outState)
    }

    private fun visibleTabs(): List<DiscoveryTab> = when (selected) {
        "featured" -> discoveryFeaturedIds.map { id -> discoveryTabs.first { it.id == id } }
        "hot" -> discoveryTabs.filter { it.id == rank }
        else -> discoveryTabs.filter { it.id == selected }
    }

    private fun refresh(ids: List<String> = visibleTabs().map { it.id }, rotate: Boolean = false) {
        loadJob?.cancel()
        states.replaceAll { _, state -> state.copy(
            loading = false,
            message = if (state.loading && state.section == null) "尚未加载此栏目" else state.message,
        ) }
        loadJob = viewLifecycleOwner.lifecycleScope.launch {
            val cache = DiscoveryCache(requireContext().applicationContext)
            val sources = withContext(Dispatchers.IO) {
                appDb.bookSourceDao.allEnabledExplore.filter {
                    it.enabled && (it.bookSourceType == 0 || it.bookSourceType == 3) && !it.exploreUrl.isNullOrBlank()
                }
            }
            val enabled = sources.map { it.bookSourceUrl }.toSet()
            states.entries.removeAll { it.value.section?.sourceUrl?.let { url -> url !in enabled } == true }
            withContext(Dispatchers.IO) { cache.read() }.filter { it.sourceUrl in enabled }.forEach {
                states.putIfAbsent(it.id, DiscoverySectionState(it))
            }
            ids.forEach { id -> states[id] = (states[id] ?: DiscoverySectionState()).begin() }
            panels.keys.forEach(::renderSection)
            val parallel = Semaphore(3)
            ids.map { id -> async {
                parallel.withPermit {
                    val tab = discoveryTabs.first { it.id == id }
                    val previous = states[id]?.section
                    val result = try {
                        withContext(Dispatchers.IO + SuppressSourceNavigation) {
                            DiscoveryLoader().load(tab, sources, previous, rotate).also { loaded ->
                                loaded.section?.let { appDb.searchBookDao.insert(*it.books.toTypedArray()) }
                            }
                        }
                    } catch (error: Exception) {
                        if (error is CancellationException) throw error
                        AppLog.put("发现页加载失败", error)
                        DiscoveryLoadResult(null, 0, "加载失败，请稍后重试")
                    }
                    states[id] = (states[id] ?: DiscoverySectionState()).complete(result)
                    renderSection(id)
                }
            } }.awaitAll()
            val snapshot = states.values.mapNotNull { it.section }
            withContext(Dispatchers.IO) {
                runCatching { cache.write(snapshot) }
                    .onFailure { AppLog.put("保存发现页缓存失败", it) }
            }
        }
    }

    private fun buildContent(): Unit = binding.hubContent.run {
        val previousX = (binding.filters.getChildAt(0) as? HorizontalScrollView)?.scrollX ?: 0
        binding.filters.removeAllViews()
        removeAllViews()
        panels.clear()
        val mainTabs = listOf("featured" to "精选") + discoveryTabs.filter { it.id !in discoveryRankIds || it.id == "hot" }.map {
            it.id to when (it.id) { "hot" -> "热榜"; "new" -> "新书"; "complete" -> "完结"; else -> it.title }
        }
        binding.filters.addChips(mainTabs, selected, previousX) { id ->
            selected = id
            buildContent()
            (parent as? ScrollView)?.scrollTo(0, 0)
            refresh()
        }
        if (selected == "hot") binding.filters.addChips(discoveryTabs.filter { it.id in discoveryRankIds }.map {
            it.id to if (it.id == "hot") "月票 / 热门" else it.title
        }, rank) { id ->
            rank = id
            buildContent()
            refresh()
        }
        visibleTabs().forEach { tab ->
            panels[tab.id] = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            addView(panels.getValue(tab.id), LinearLayout.LayoutParams(-1, -2))
            renderSection(tab.id)
        }
        val footer = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
        footer.addView(textButton("刷新发现") { refresh() }, LinearLayout.LayoutParams(0, 48.dpToPx(), 1f))
        footer.addView(textButton("全部书源榜单 ›") { startActivity<AllExploreActivity>() }, LinearLayout.LayoutParams(0, 48.dpToPx(), 1f))
        addView(footer)
        note("排名与完结状态以来源为准；缓存会显示时间。")
    }

    private fun LinearLayout.addChips(
        tabs: List<Pair<String, String>>, active: String, offset: Int = 0, choose: (String) -> Unit,
    ) {
        val row = LinearLayout(context)
        tabs.forEach { (id, title) ->
            row.addView(textButton(title) { choose(id) }.apply {
                minWidth = 56.dpToPx()
                setPadding(12.dpToPx(), 0, 12.dpToPx(), 0)
                if (active == id) {
                    setTypeface(typeface, Typeface.BOLD)
                    background = GradientDrawable().apply {
                        cornerRadius = 18.dpToPx().toFloat()
                        setColor((context.primaryColor and 0xffffff) or 0x30000000)
                    }
                } else setTextColor(context.secondaryTextColor)
            }, LinearLayout.LayoutParams(-2, 44.dpToPx()).apply { marginEnd = 4.dpToPx() })
        }
        addView(HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            addView(row)
            post { scrollTo(offset, 0) }
        }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 4.dpToPx() })
    }

    private fun renderSection(id: String) {
        val panel = panels[id] ?: return
        val tab = discoveryTabs.first { it.id == id }
        val state = states[id] ?: DiscoverySectionState(loading = true)
        val section = state.section
        val scroll = binding.hubContent.parent as? ScrollView
        val offset = scroll?.scrollY ?: 0
        panel.apply {
            removeAllViews()
            val header = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
            header.addView(TextView(context).apply {
                text = tab.title
                textSize = 20f
                gravity = Gravity.CENTER_VERTICAL
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(context.primaryTextColor)
            }, LinearLayout.LayoutParams(0, 48.dpToPx(), 1f))
            header.addView(textButton(if (state.loading) "更新中" else "换一批") { refresh(listOf(id), true) }.apply {
                isEnabled = !state.loading
            }, LinearLayout.LayoutParams(60.dpToPx(), 48.dpToPx()))
            if (section != null) header.addView(textButton("更多 ›") { openList(section) }, LinearLayout.LayoutParams(60.dpToPx(), 48.dpToPx()))
            addView(header)
            val preview = section?.books?.take(if (selected == "featured") 6 else 12)
            if (preview != null || state.loading) {
                val count = preview?.size ?: 6
                repeat((count + 2) / 3) { rowIndex ->
                    val row = LinearLayout(context)
                    repeat(3) { column ->
                        val index = rowIndex * 3 + column
                        val book = preview?.getOrNull(index)
                        val tile = when {
                            book != null -> DiscoveryBookView(context).apply { bind(book, index + 1) { openBook(book) } }
                            preview == null -> DiscoveryBookView(context).apply { showLoading() }
                            else -> View(context)
                        }
                        row.addView(tile, LinearLayout.LayoutParams(0, -2, 1f).apply { if (column < 2) marginEnd = 12.dpToPx() })
                    }
                    addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 12.dpToPx() })
                }
            }
            if (section != null) {
                val date = SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date(section.updatedAt))
                note("${section.sourceName} · ${section.categoryTitle}\n$date ${if (state.fresh) "已更新" else "缓存"}${if (state.loading) " · 正在刷新" else ""}").textSize = 12f
            }
            state.message?.let { message ->
                note(if (section != null) "$message，已保留上次内容。" else message).textSize = 13f
                addView(textButton("重试此栏目") { refresh(listOf(id)) }, LinearLayout.LayoutParams(-1, 44.dpToPx()))
                if (section == null) addView(textButton("管理发现书源 ›") { startActivity<BookSourceActivity>() }, LinearLayout.LayoutParams(-1, 44.dpToPx()))
            }
        }
        scroll?.post { scroll.scrollTo(0, offset) }
    }

    private fun textButton(label: String, action: () -> Unit) = TextView(requireContext()).apply {
        text = label
        textSize = 14f
        gravity = Gravity.CENTER
        setTextColor(context.primaryTextColor)
        isFocusable = true
        val attributes = context.obtainStyledAttributes(intArrayOf(android.R.attr.selectableItemBackground))
        foreground = attributes.getDrawable(0)
        attributes.recycle()
        setOnClickListener { action() }
    }

    private fun openList(section: DiscoverySection) = startActivity<DiscoveryListActivity> {
        putExtra("sectionId", section.id)
        putExtra("sourceUrl", section.sourceUrl)
        putExtra("sourceName", section.sourceName)
        putExtra("exploreName", section.categoryTitle)
        putExtra("exploreUrl", section.categoryUrl)
    }

    private fun openBook(book: SearchBook) {
        viewLifecycleOwner.lifecycleScope.launch {
            withContext(Dispatchers.IO) { appDb.searchBookDao.insert(book) }
            startActivity<BookInfoActivity> {
                putExtra("name", book.name)
                putExtra("author", book.author)
                putExtra("bookUrl", book.bookUrl)
            }
        }
    }

    companion object {
        fun create(position: Int) = PlazaFragment().apply { arguments = Bundle().apply { putInt("position", position) } }
    }
}
