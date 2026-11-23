package io.legado.app.help.config

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.pow

class ReaderPresetsTest {
    @Test fun allPresetsKeepBodyTextReadable() {
        fun luminance(hex: String): Double {
            val value = hex.removePrefix("#").toInt(16)
            val channels = listOf(value shr 16 and 255, value shr 8 and 255, value and 255).map {
                val v = it / 255.0
                if (v <= 0.04045) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
            }
            return channels[0] * 0.2126 + channels[1] * 0.7152 + channels[2] * 0.0722
        }
        readerPresets.forEach { preset ->
            val bg = luminance(preset.background)
            val text = luminance(preset.text)
            val contrast = (maxOf(bg, text) + 0.05) / (minOf(bg, text) + 0.05)
            assertTrue("${preset.name}: contrast $contrast", contrast >= 4.5)
            assertTrue(preset.size >= 20)
        }
    }
}
