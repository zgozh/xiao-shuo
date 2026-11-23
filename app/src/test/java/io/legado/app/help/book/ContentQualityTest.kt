package io.legado.app.help.book

import org.junit.Assert.*
import org.junit.Test

class ContentQualityTest {
    @Test fun ordinaryChineseTextIsNotFlagged() {
        assertTrue(contentQualityWarnings("这是正常的小说正文，夜色渐深，故事仍在继续。".repeat(30)).isEmpty())
    }

    @Test fun shortChapterIsOnlyAWarning() {
        assertTrue(contentQualityWarnings("尾声。回家了。").any { it.contains("较短") })
    }

    @Test fun replacementCharactersAreDetectedWithoutRejectingOneTypo() {
        assertTrue(contentQualityWarnings("正常文字".repeat(60) + "\uFFFD").isEmpty())
        assertTrue(contentQualityWarnings("正常文字".repeat(60) + "\uFFFD".repeat(12)).any { it.contains("编码") })
    }

    @Test fun privateFontMappingAndChallengePagesAreFlagged() {
        assertTrue(contentQualityWarnings("\uE001".repeat(150)).any { it.contains("解混淆") })
        assertTrue(contentQualityWarnings("Access Denied: 请完成人机验证").any { it.contains("验证页") })
    }
}
