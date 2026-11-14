package io.legado.app.ui.main.hub

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.legado.app.data.appDb
import io.legado.app.help.source.SuppressSourceNavigation
import io.legado.app.help.source.exploreKinds
import io.legado.app.model.webBook.WebBook
import io.legado.app.utils.GSON
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Opt-in maintenance probe. Normal test runs never depend on external websites. */
@RunWith(AndroidJUnit4::class)
class DiscoveryLiveProbeTest {
    @Test fun probeCurrentDiscoverySources() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("goubinDiscoveryProbe") == "1")
        val output = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "discovery-probe.json")
        val reports = mutableListOf<Map<String, Any>>()
        val lock = Mutex()
        val parallel = Semaphore(6)
        val patterns = listOf(Regex("排行|点击|热门|月票|推荐|总榜"), Regex("新书|最新|更新"), Regex("完结|全本|完本"))
        val sources = appDb.bookSourceDao.allEnabledExplore.filter {
            it.enabled && it.bookSourceType == 0 && !it.exploreUrl.isNullOrBlank() && it.ruleExplore != null
        }.sortedBy { source ->
            if (listOf("速读谷", "书海阁", "八零", "笔迷", "起点", "快书").any { source.bookSourceName.contains(it) }) 0 else 1
        }.take(48)
        sources.map { source -> async(Dispatchers.IO + SuppressSourceNavigation) {
            parallel.withPermit {
                val sections = mutableListOf<Map<String, Any>>()
                val report = linkedMapOf<String, Any>("name" to source.bookSourceName, "url" to source.bookSourceUrl)
                try {
                    val kinds = withTimeout(4000) { source.exploreKinds() }.filter { it.type == "url" && !it.url.isNullOrBlank() && !it.title.startsWith("ERROR:") }
                    report["categories"] = kinds.map { it.title }
                    val selected = patterns.mapNotNull { regex -> kinds.firstOrNull { regex.containsMatchIn(it.title) } }.distinctBy { it.url }
                    selected.forEach { kind ->
                        try {
                            val books = withTimeout(6000) { WebBook.exploreBookAwait(source, kind.url!!, 1) }
                            sections += mapOf("category" to kind.title, "categoryUrl" to kind.url!!, "count" to books.size, "books" to books.take(6))
                        } catch (error: Exception) {
                            sections += mapOf("category" to kind.title, "error" to error.toString(), "count" to 0)
                        }
                    }
                } catch (error: Exception) { report["error"] = error.toString() }
                report["sections"] = sections
                lock.withLock {
                    reports += report
                    output.writeText(GSON.toJson(reports))
                    Log.i("GoubinDiscoveryProbe", "${source.bookSourceName}: ${sections.map { it["count"] }}")
                }
            }
        } }.awaitAll()
        assertTrue("No live discovery endpoint returned books; inspect ${output.path}", reports.any {
            @Suppress("UNCHECKED_CAST")
            (it["sections"] as List<Map<String, Any>>).any { section -> (section["count"] as Int) > 0 }
        })
    }
}
