package dev.anvas.orbitrack.idea

import dev.anvas.orbitrack.idea.services.normaliseGhPaginatedJson
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for the [normaliseGhPaginatedJson] utility function.
 *
 * `gh api --paginate` concatenates one JSON array per page without any separator,
 * producing output like `[{"id":1}][{"id":2}]`.  The helper must collapse this
 * into a single valid array before kotlinx.serialization can parse it.
 */
class GhJsonNormalisationTest {

    // ---- single-page pass-through ----

    @Test
    fun `single array passes through unchanged`() {
        val input = """[{"id":1},{"id":2}]"""
        assertEquals(input, normaliseGhPaginatedJson(input))
    }

    @Test
    fun `empty single array passes through unchanged`() {
        val input = "[]"
        assertEquals("[]", normaliseGhPaginatedJson(input))
    }

    @Test
    fun `single object passes through unchanged`() {
        val input = """{"id":1}"""
        assertEquals(input, normaliseGhPaginatedJson(input))
    }

    // ---- multi-page concatenation ----

    @Test
    fun `two pages are merged into one array`() {
        val page1 = """[{"id":1},{"id":2}]"""
        val page2 = """[{"id":3}]"""
        val result = normaliseGhPaginatedJson("$page1$page2")
        assertEquals("""[{"id":1},{"id":2},{"id":3}]""", result)
    }

    @Test
    fun `three pages are merged into one array`() {
        val input = """[{"id":1}][{"id":2}][{"id":3}]"""
        val result = normaliseGhPaginatedJson(input)
        assertEquals("""[{"id":1},{"id":2},{"id":3}]""", result)
    }

    @Test
    fun `pages separated by newline whitespace are merged`() {
        val input = "[{\"id\":1}]\n[{\"id\":2}]"
        val result = normaliseGhPaginatedJson(input)
        assertEquals("""[{"id":1},{"id":2}]""", result)
    }

    @Test
    fun `pages separated by multiple spaces are merged`() {
        val input = """[{"id":1}]   [{"id":2}]"""
        val result = normaliseGhPaginatedJson(input)
        assertEquals("""[{"id":1},{"id":2}]""", result)
    }

    // ---- empty page handling ----

    @Test
    fun `empty second page is collapsed gracefully`() {
        val input = """[{"id":1}][]"""
        val result = normaliseGhPaginatedJson(input)
        // Should not produce invalid JSON like [{"id":1},]
        val trimmed = result.trim()
        assertValidJsonArray(trimmed)
    }

    @Test
    fun `two empty pages produce empty array`() {
        val input = "[][]"
        val result = normaliseGhPaginatedJson(input)
        assertValidJsonArray(result.trim())
    }

    // ---- leading / trailing whitespace ----

    @Test
    fun `leading and trailing whitespace is stripped`() {
        val input = "  [{\"id\":1}]  "
        val result = normaliseGhPaginatedJson(input)
        assertEquals("""[{"id":1}]""", result)
    }

    // ---- round-trip with kotlinx.serialization ----

    @Test
    fun `merged output can be parsed by kotlinx-serialization`() {
        val page1 = """[{"id":1,"login":"alice"},{"id":2,"login":"bob"}]"""
        val page2 = """[{"id":3,"login":"carol"}]"""
        val merged = normaliseGhPaginatedJson("$page1$page2")

        @kotlinx.serialization.Serializable
        data class Item(val id: Int, val login: String)

        val items = kotlinx.serialization.json.Json.decodeFromString<List<Item>>(merged)
        assertEquals(3, items.size)
        assertEquals("alice", items[0].login)
        assertEquals("carol", items[2].login)
    }

    // ---- helpers ----

    /** Very lightweight structural check — not a full JSON parser. */
    private fun assertValidJsonArray(json: String) {
        assert(json.startsWith("[")) { "Expected JSON array but got: $json" }
        assert(json.endsWith("]")) { "Expected JSON array but got: $json" }
        assert(!json.contains("[,")) { "Invalid leading comma in: $json" }
        assert(!json.contains(",]")) { "Invalid trailing comma in: $json" }
    }
}

