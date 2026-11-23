package io.legado.app.ui.main.hub

import org.junit.Assert.*
import org.junit.Test

class DiscoveryGuestSessionTest {
    @Test fun initializesMissingGuestTokenOnlyOnce() {
        var token = ""
        var writes = 0
        repeat(3) {
            ensureDiscoveryGuestSession("https://m.qidian.com##部分限免源", { token }) {
                token = it
                writes++
            }
        }
        assertTrue(token.matches(Regex("[0-9a-f]{32}")))
        assertEquals(1, writes)
    }

    @Test fun preservesExistingSessionToken() {
        ensureDiscoveryGuestSession("https://m.qidian.com##部分限免源", { "existing-session" }) {
            fail("An existing session must not be overwritten")
        }
    }

    @Test fun unrelatedSourcesDoNotReadOrWriteQidianCookies() {
        ensureDiscoveryGuestSession("https://example.com", { error("must not read") }) {
            fail("must not write")
        }
    }
}
