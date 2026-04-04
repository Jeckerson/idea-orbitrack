package dev.anvas.orbitrack.idea

import dev.anvas.orbitrack.idea.cache.CachedFilterState
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure unit tests for [CachedFilterState] — no IntelliJ Platform required.
 */
class CachedFilterStateTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun `filtersCollapsed defaults to false`() {
        val state = CachedFilterState()
        assertFalse("filtersCollapsed should default to false", state.filtersCollapsed)
    }

    @Test
    fun `filtersCollapsed can be set to true`() {
        val state = CachedFilterState(filtersCollapsed = true)
        assertTrue(state.filtersCollapsed)
    }

    @Test
    fun `copy preserves filtersCollapsed`() {
        val original = CachedFilterState(typeIndex = 1, filtersCollapsed = true)
        val copied = original.copy(stateIndex = 2)
        assertTrue("copy should preserve filtersCollapsed", copied.filtersCollapsed)
        assertEquals(1, copied.typeIndex)
        assertEquals(2, copied.stateIndex)
    }

    @Test
    fun `serialization includes filtersCollapsed when true`() {
        val state = CachedFilterState(filtersCollapsed = true)
        val serialized = json.encodeToString(CachedFilterState.serializer(), state)
        assertTrue("Serialized JSON must contain filtersCollapsed", serialized.contains("filtersCollapsed"))
        assertTrue("filtersCollapsed must be true in JSON", serialized.contains("\"filtersCollapsed\":true"))
    }

    @Test
    fun `deserialization restores filtersCollapsed true`() {
        val raw = """{"filtersCollapsed":true,"sortField":"UPDATED","sortDirection":"DESC","groupModes":["BY_TYPE"]}"""
        val state = json.decodeFromString<CachedFilterState>(raw)
        assertTrue(state.filtersCollapsed)
    }

    @Test
    fun `deserialization defaults filtersCollapsed to false when field absent`() {
        // Simulates an old cache file that predates the field
        val raw = """{"sortField":"UPDATED","sortDirection":"DESC","groupModes":["BY_TYPE"]}"""
        val state = json.decodeFromString<CachedFilterState>(raw)
        assertFalse("Missing field must default to false", state.filtersCollapsed)
    }

    @Test
    fun `round-trip serialization preserves all fields`() {
        val original = CachedFilterState(
            selectedOrg = "my-org",
            selectedRepo = "my-org/my-repo",
            typeIndex = 2,
            stateIndex = 1,
            sortField = "CREATED",
            sortDirection = "ASC",
            groupModes = listOf("BY_ORG", "BY_REPO"),
            filtersCollapsed = true,
        )
        val serialized = json.encodeToString(CachedFilterState.serializer(), original)
        val restored = json.decodeFromString<CachedFilterState>(serialized)
        assertEquals(original, restored)
    }
}

