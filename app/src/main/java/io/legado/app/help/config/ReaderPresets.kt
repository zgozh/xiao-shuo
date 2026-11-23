package io.legado.app.help.config

internal data class ReaderPreset(
    val name: String, val summary: String, val background: String, val text: String,
    val size: Int = 21, val lineSpacing: Int = 14, val paragraphSpacing: Int = 10,
    val padding: Int = 20, val dark: Boolean = false,
)

internal val readerPresets = listOf(
    ReaderPreset("夸克风格 · 清爽", "浅灰纸面，清晰留白", "#F8F8F6", "#30343B", size = 20),
    ReaderPreset("起点风格 · 纸感", "暖黄纸面，舒展排版", "#F4E8D1", "#3B3329", padding = 22),
    ReaderPreset("番茄风格 · 舒适", "柔和米白，适合长篇", "#F6EFE4", "#423B33", lineSpacing = 13),
    ReaderPreset("微信读书风格 · 简洁", "近白纸面，宽边距", "#FAF9F5", "#282C30", size = 20, padding = 24),
    ReaderPreset("护眼绿", "低饱和绿，大段阅读", "#DDE8D4", "#28372B", lineSpacing = 15),
    ReaderPreset("经典羊皮纸", "复古暖纸，深棕正文", "#E8D9B9", "#3C3125", lineSpacing = 15),
    ReaderPreset("深夜柔和", "深色纸面，柔灰文字", "#15181E", "#B5BBC3", dark = true),
    ReaderPreset("大字舒展", "大字号、宽行距", "#F7F4EA", "#292D29", size = 26, lineSpacing = 18, paragraphSpacing = 14),
)

internal fun ReaderPreset.toReadConfig() = ReadBookConfig.Config(
    name = name, bgStr = background, textColor = text,
    bgStrNight = if (dark) background else "#17191D", textColorNight = "#B5BBC3",
    textAccentColor = "#846238", textAccentColorNight = "#C5AD82",
    darkStatusIcon = !dark, darkStatusIconNight = false,
    textSize = size, lineSpacingExtra = lineSpacing, paragraphSpacing = paragraphSpacing,
    letterSpacing = 0f, paddingLeft = padding, paddingRight = padding,
    paddingTop = 12, paddingBottom = 12, titleMode = 1,
)
