package io.legado.app.ui.main.hub

import android.os.Bundle
import io.legado.app.base.BaseActivity
import io.legado.app.databinding.ActivityReaderHubBinding
import io.legado.app.ui.book.read.config.BgTextConfigDialog
import io.legado.app.ui.book.read.config.ReadStyleDialog
import io.legado.app.ui.config.ConfigActivity
import io.legado.app.ui.config.ConfigTag
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.startActivity
import io.legado.app.utils.viewbindingdelegate.viewBinding

class AppearanceActivity : BaseActivity<ActivityReaderHubBinding>() {
    override val binding by viewBinding(ActivityReaderHubBinding::inflate)

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        binding.titleBar.title = "主题与阅读个性化"
        binding.hubContent.apply {
            heading("打造你的专属书房")
            note("主页保持简洁，细节由你决定。应用界面与阅读页面可分别设置。")
            action("一键套用常用阅读模板") { showReaderPresetPicker() }
            note("提供夸克、起点、番茄等参考风格，以及护眼、纸张、夜读和大字模板。模板为独立设计，可继续微调。")
            card("应用主题与背景", "日间 / 夜间 / 跟随系统 / 墨水屏；主题色、强调色、背景图、模糊、透明度、底栏皮肤与界面缩放。") {
                openConfig(ConfigTag.THEME_CONFIG)
            }
            action("打开全部界面主题设置") { openConfig(ConfigTag.THEME_CONFIG) }
            card("字体与排版", "导入 TTF / OTF 字体，调整字号、字重、字间距、行距、段距、首行缩进和翻页方式。") {
                showDialogFragment<ReadStyleDialog>()
            }
            action("打开阅读字体与排版") { showDialogFragment<ReadStyleDialog>() }
            card("阅读背景与配色", "纯色、自选图片、文字颜色、强调色、段评图标与下划线配色；创建多套方案，并导入或导出配置。") {
                showDialogFragment<BgTextConfigDialog>()
            }
            action("管理阅读背景与配色") { showDialogFragment<BgTextConfigDialog>() }
            action("封面、书架与封面字体") { openConfig(ConfigTag.COVER_CONFIG) }
            action("启动页与欢迎背景") { openConfig(ConfigTag.WELCOME_CONFIG) }
            action("更多阅读与操作设置") { openConfig(ConfigTag.OTHER_CONFIG) }
            action("备份与 WebDAV 同步") { openConfig(ConfigTag.BACKUP_CONFIG) }
            note("阅读时也可点击页面中央打开菜单，设置页边距、状态栏、亮度、自动翻页及单本书的阅读选项。")
        }
    }

    private fun openConfig(tag: String) = startActivity<ConfigActivity> { putExtra("configTag", tag) }
}
