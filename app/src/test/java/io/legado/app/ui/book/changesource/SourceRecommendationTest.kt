package io.legado.app.ui.book.changesource

import org.junit.Assert.*
import org.junit.Test

class SourceRecommendationTest {
    @Test fun evidenceAndWarningsDetermineOrder() {
        fun rank(status: String?, warning: Boolean = false, measured: Int = -1, author: Boolean = true) =
            sourceRecommendationTier(author, status, warning, measured)
        assertTrue(rank(null, measured = 1500) > rank("PASSED"))
        assertTrue(rank("PASSED") > rank(null))
        assertTrue(rank(null) > rank("PASSED", warning = true))
        assertTrue(rank("PASSED", warning = true) > rank("FAILED"))
        assertEquals(0, rank("PASSED", measured = 1500, author = false))
        assertEquals(1, rank("PASSED", warning = true, measured = 1500))
    }
}
