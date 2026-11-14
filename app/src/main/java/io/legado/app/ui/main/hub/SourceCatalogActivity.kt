package io.legado.app.ui.main.hub

import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import io.legado.app.base.BaseActivity
import io.legado.app.databinding.ActivityReaderHubBinding
import io.legado.app.help.http.okHttpClient
import io.legado.app.ui.association.ImportBookSourceDialog
import io.legado.app.ui.book.source.manage.BookSourceActivity
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.startActivity
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

class SourceCatalogActivity : BaseActivity<ActivityReaderHubBinding>() {
    override val binding by viewBinding(ActivityReaderHubBinding::inflate)
    private val cachedCatalog get() = File(filesDir, "goubin-source-catalog.json")

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        binding.titleBar.title = "书源中心"
        render(runCatching { parseCatalog(cachedCatalog.readText()) }.getOrElse {
            parseCatalog(assets.open("source-catalog.json").bufferedReader().use { it.readText() })
        })
    }

    private fun render(repositories: List<SourceRepository>, message: String? = null) {
        binding.hubContent.apply {
            removeAllViews()
            heading("连接更多好故事")
            note("从目录获取规则，预览后批量导入。导入的启用书源会自动参与聚合搜索。")
            if (message != null) note(message)
            val refresh = action("更新书源目录") { }
            refresh.setOnClickListener {
                refresh.isEnabled = false
                refresh.text = "正在更新…"
                lifecycleScope.launch {
                    val result = withContext(Dispatchers.IO) {
                        runCatching {
                            val client = okHttpClient.newBuilder().callTimeout(30, TimeUnit.SECONDS).build()
                            client.newCall(Request.Builder().url(CATALOG_URL).build()).execute().use { response ->
                                check(response.isSuccessful) { "HTTP ${response.code}" }
                                val json = response.body.string()
                                val parsed = parseCatalog(json)
                                cachedCatalog.writeText(json)
                                parsed
                            }
                        }
                    }
                    result.fold(
                        onSuccess = { render(it, "目录已更新，${it.size} 个规则集合。") },
                        onFailure = { render(repositories, "更新失败：${it.localizedMessage}。仍可使用当前目录或手动导入。") }
                    )
                }
            }
            repositories.forEach { repository ->
                card(repository.name, "${repository.description}\n规则地址：${repository.url}") {
                    showDialogFragment(ImportBookSourceDialog(repository.url))
                }
            }
            action("从网址导入书源") { importUrl() }
            action("管理、检测与订阅书源") { startActivity<BookSourceActivity>() }
            heading("提高搜索与正文质量", 19f)
            note("1. 导入后在书源管理中检测搜索、目录和正文。\n2. 优先启用响应正常的来源，失效来源可停用。\n3. 章节缺失、乱码或删节时，在阅读菜单中换源对照。\n4. 在书源管理中添加订阅，后续启动时检查规则更新。")
            note("书源规则包含脚本，请选择可信维护者。应用不要求登录，不提供付费内容解锁。")
        }
    }

    private fun importUrl() {
        val input = EditText(this).apply {
            hint = "https://…/bookSource.json"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setSingleLine()
        }
        val dialog = AlertDialog.Builder(this).setTitle("导入书源网址").setView(input)
            .setNegativeButton("取消", null).setPositiveButton("获取", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val url = input.text.toString().trim()
                if (!url.startsWith("https://") && !url.startsWith("http://")) {
                    input.error = "请输入完整的 HTTP 或 HTTPS 地址"
                } else {
                    dialog.dismiss()
                    showDialogFragment(ImportBookSourceDialog(url))
                }
            }
        }
        dialog.show()
    }

    companion object {
        const val CATALOG_URL = "https://raw.githubusercontent.com/zgozh/xiao-shuo/main/sources/catalog.json"
    }
}
