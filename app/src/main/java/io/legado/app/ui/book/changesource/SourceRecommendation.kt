package io.legado.app.ui.book.changesource

/** Ranking evidence is deliberately separate from any claim of an unabridged edition. */
internal fun sourceRecommendationTier(
    authorMatches: Boolean, checkStatus: String?, hasQualityWarning: Boolean, measuredCharacters: Int,
): Int = when {
    !authorMatches -> 0
    hasQualityWarning -> 1
    measuredCharacters >= 300 -> 4
    checkStatus == "PASSED" -> 3
    checkStatus == "FAILED" -> 0
    else -> 2
}
