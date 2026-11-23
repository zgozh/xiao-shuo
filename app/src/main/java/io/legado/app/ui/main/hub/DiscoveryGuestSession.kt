package io.legado.app.ui.main.hub

import io.legado.app.help.http.CookieStore
import java.util.UUID

private val guestSessionLock = Any()

/** Qidian's public rank API requires the same guest CSRF token in its cookie and query. */
internal fun ensureDiscoveryGuestSession(
    sourceUrl: String,
    readToken: () -> String = { CookieStore.getKey("https://qidian.com", "_csrfToken") },
    saveToken: (String) -> Unit = { CookieStore.replaceCookie("https://qidian.com", "_csrfToken=$it") },
) {
    if (sourceUrl != "https://m.qidian.com##部分限免源") return
    synchronized(guestSessionLock) {
        if (readToken().isBlank()) saveToken(UUID.randomUUID().toString().replace("-", ""))
    }
}
