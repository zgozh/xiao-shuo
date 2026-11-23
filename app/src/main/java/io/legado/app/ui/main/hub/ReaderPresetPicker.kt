package io.legado.app.ui.main.hub

import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import io.legado.app.constant.EventBus
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.help.config.ReaderPreset
import io.legado.app.help.config.readerPresets
import io.legado.app.help.config.toReadConfig
import io.legado.app.ui.book.read.ReadBookActivity
import io.legado.app.utils.dpToPx
import io.legado.app.utils.postEvent
import io.legado.app.utils.toastOnUi

internal fun AppCompatActivity.showReaderPresetPicker(onApplied: () -> Unit = {}) {
    val adapter = object : ArrayAdapter<ReaderPreset>(this, android.R.layout.simple_list_item_1, readerPresets) {
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View =
            TextView(context).apply {
                val preset = readerPresets[position]
                text = "${preset.name}\n${preset.summary} · ${preset.size}号字\n山色入窗，故事正长。"
                textSize = 17f
                setLineSpacing(6.dpToPx().toFloat(), 1.15f)
                setPadding(20.dpToPx(), 16.dpToPx(), 20.dpToPx(), 16.dpToPx())
                setTextColor(Color.parseColor(preset.text))
                setBackgroundColor(Color.parseColor(preset.background))
            }
    }
    AlertDialog.Builder(this).setTitle("一键阅读模板 · 点击套用")
        .setAdapter(adapter) { _, index ->
            val preset = readerPresets[index]
            val config = preset.toReadConfig()
            val existing = ReadBookConfig.configList.indexOfFirst { it.name == preset.name }
            val target = if (existing >= 0) existing else ReadBookConfig.configList.size.also {
                ReadBookConfig.configList.add(config)
            }
            ReadBookConfig.styleSelect = target
            ReadBookConfig.durConfig = config
            ReadBookConfig.save()
            postEvent(EventBus.UP_CONFIG, arrayListOf(1, 2, 5, 8))
            postEvent(EventBus.UPDATE_READ_ACTION_BAR, true)
            (this as? ReadBookActivity)?.upPageAnim()
            toastOnUi("已套用 ${preset.name}，仍可继续微调")
            onApplied()
        }.setNegativeButton("关闭", null).show()
}
