package io.legado.app.ui.main.hub

import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.base.BaseFragment
import io.legado.app.data.appDb
import io.legado.app.databinding.FragmentReadingHubBinding
import io.legado.app.ui.about.ReadRecordActivity
import io.legado.app.ui.book.search.SearchActivity
import io.legado.app.ui.book.source.manage.BookSourceActivity
import io.legado.app.ui.main.MainFragmentInterface
import io.legado.app.utils.dpToPx
import io.legado.app.utils.startActivity
import io.legado.app.utils.startActivityForBook
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ReadingHubFragment : BaseFragment(R.layout.fragment_reading_hub), MainFragmentInterface {
    override val position: Int? get() = arguments?.getInt("position")
    internal val dashboard get() = arguments?.getBoolean("dashboard") == true
    private val binding by viewBinding(FragmentReadingHubBinding::bind)

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        binding.titleBar.title = if (dashboard) "阅读面板" else "全源搜索"
        setSupportToolbar(binding.titleBar.toolbar)
    }

    override fun onResume() {
        super.onResume()
        viewLifecycleOwner.lifecycleScope.launch {
            val snapshot = withContext(Dispatchers.IO) {
                Triple(appDb.bookDao.allShelfByOrder, appDb.bookSourceDao.allPart, appDb.readRecordDao.allShow)
            }
            val (books, sources, records) = snapshot
            binding.hubContent.apply {
                removeAllViews()
                if (!dashboard) {
                    heading("好故事，从这里开始")
                    note("同时搜索已启用的书源，支持书名、作者与关键词。")
                    var scope = ""
                    val ranges = RadioGroup(context).apply {
                        orientation = RadioGroup.HORIZONTAL
                        listOf("全部书源" to "", "日本轻小说" to "日本轻小说").forEach { (label, value) ->
                            addView(RadioButton(context).apply {
                                id = View.generateViewId()
                                text = label
                                isChecked = value.isEmpty()
                                setOnCheckedChangeListener { _, checked -> if (checked) scope = value }
                            })
                        }
                    }
                    addView(ranges)
                    note("日本轻小说：优先查找中文译本；原文名、简繁体和不同译名可能影响命中。搜索页右上角也可切换书源分组。")
                    val input = EditText(context).apply {
                        hint = "输入书名或作者"
                        textSize = 18f
                        setSingleLine()
                        imeOptions = EditorInfo.IME_ACTION_SEARCH
                        minHeight = 56.dpToPx()
                        setOnEditorActionListener { _, action, _ ->
                            if (action == EditorInfo.IME_ACTION_SEARCH) {
                                SearchActivity.start(context, text.toString().trim(), scope)
                                true
                            } else false
                        }
                    }
                    addView(input, LinearLayout.LayoutParams(-1, -2))
                    action("按所选范围搜索") {
                        SearchActivity.start(context, input.text.toString().trim(), scope)
                    }
                    card("书源状态", "已启用 ${sources.count { it.enabled }} / ${sources.size} 条规则\n检测通过 ${sources.count { it.checkStatus == "PASSED" }} 条 · 其余状态见书源管理\n启用不代表已经实测可读。") {
                        startActivity<BookSourceActivity>()
                    }
                    action("获取与更新书源") { startActivity<SourceCatalogActivity>() }
                    heading("按兴趣找书", 19f)
                    listOf("玄幻", "仙侠", "都市", "言情", "历史", "科幻", "悬疑", "轻小说").forEach { genre ->
                        action(genre) { SearchActivity.start(context, genre) }
                    }
                    note("分类关键词会交给各书源搜索；分类榜单位于「发现」，内容取决于书源支持。")
                } else {
                    heading("把时间留给阅读")
                    val minutes = records.sumOf { it.readTime }.coerceAtLeast(0) / 60000
                    card("${minutes / 60} 小时 ${minutes % 60} 分钟", "累计阅读 · ${records.size} 部作品\n书架 ${books.size} 本 · 已启用书源 ${sources.count { it.enabled }} 个")
                    action("查看完整阅读记录") { startActivity<ReadRecordActivity>() }
                    heading("继续阅读", 19f)
                    val readKeys = records.map { it.bookName to it.author }.toSet()
                    val recent = books.filter { (it.name to it.author) in readKeys }.sortedByDescending { it.durChapterTime }.take(3)
                    if (recent.isEmpty()) note("开始阅读后，这里会显示最近阅读的作品。")
                    recent.forEach { book ->
                        card(book.name, "${book.author}\n${book.durChapterTitle.orEmpty()}") {
                            context.startActivityForBook(book)
                        }
                    }
                    heading("你的阅读排行", 19f)
                    if (records.isEmpty()) note("读书时长会自动记录，排行来自你的真实阅读记录。")
                    records.sortedByDescending { it.readTime }.take(5).forEachIndexed { index, record ->
                        card("${index + 1}. ${record.bookName}", "${record.author} · ${record.readTime / 60000} 分钟") {
                            SearchActivity.start(context, record.bookName)
                        }
                    }
                    heading("根据你的阅读推荐", 19f)
                    val preferredAuthors = recent.map { it.author }.filter { it.isNotBlank() }.toSet()
                    val preferredKinds = recent.flatMap { it.kind.orEmpty().split(',', '，', '/', ' ') }.filter { it.isNotBlank() }.toSet()
                    val unread = books.filter { (it.name to it.author) !in readKeys }.sortedByDescending { book ->
                        (if (book.author in preferredAuthors) 2 else 0) + preferredKinds.count { book.kind.orEmpty().contains(it) }
                    }.take(4)
                    if (unread.isEmpty()) note("书架中暂无待读作品。添加更多书籍后，可根据最近阅读的作者与分类推荐待读书。")
                    unread.forEach { book ->
                        val reason = if (book.author in preferredAuthors) "与你最近阅读的作品同作者" else if (preferredKinds.any { book.kind.orEmpty().contains(it) }) "与你最近阅读的分类相近" else "书架中的待读作品"
                        card(book.name, "$reason\n${book.author}") { context.startActivityForBook(book) }
                    }
                    action("寻找更多作品") { SearchActivity.start(context, recent.firstOrNull()?.author) }
                }
            }
        }
    }

    companion object {
        fun create(position: Int, dashboard: Boolean) = ReadingHubFragment().apply {
            arguments = Bundle().apply {
                putInt("position", position)
                putBoolean("dashboard", dashboard)
            }
        }
    }
}
