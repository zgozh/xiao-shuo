package io.legado.app.ui.main.hub

import org.junit.Assert.*
import org.junit.Test

class SourceRepositoryTest {
    @Test fun parsesAndDeduplicatesRepositories() {
        val entry = "{\"name\":\"来源\",\"url\":\"https://example.com/sources.json\"}"
        val repositories = parseCatalog("{\"version\":1,\"repositories\":[$entry,$entry]}")
        assertEquals(1, repositories.size)
        assertEquals("来源", repositories.single().name)
    }

    @Test fun rejectsInvalidRemoteCatalogWithoutReturningPartialEntries() {
        listOf(
            "{\"version\":2,\"repositories\":[]}",
            "{\"version\":1,\"repositories\":[]}",
            "{\"version\":1,\"repositories\":[{\"name\":\"a\",\"url\":\"file:///tmp/a.json\"}]}",
            "{\"version\":1,\"repositories\":[{\"name\":\" \",\"url\":\"https://example.com\"}]}"
        ).forEach { assertTrue(runCatching { parseCatalog(it) }.isFailure) }
    }
}
