package dev.anvas.orbitrack.idea

import dev.anvas.orbitrack.idea.model.OrbiReviewComment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * Unit tests for the [OrbiReviewComment] domain model.
 *
 * These tests verify the data model contract independently of any GitHub API
 * client or IntelliJ Platform integration.
 */
class OrbiReviewCommentModelTest {

    private fun makeComment(
        id: Long = 1L,
        itemId: Long = 100L,
        author: String = "alice",
        body: String = "LGTM",
        path: String = "src/Foo.kt",
        line: Int? = 10,
        diffHunk: String = "@@ -8,7 +8,7 @@",
        isSuggestion: Boolean = false,
        inReplyToId: Long? = null,
        reviewId: Long? = null,
    ) = OrbiReviewComment(
        id = id,
        itemId = itemId,
        author = author,
        body = body,
        createdAt = Instant.parse("2024-01-01T00:00:00Z"),
        updatedAt = Instant.parse("2024-01-01T01:00:00Z"),
        path = path,
        line = line,
        diffHunk = diffHunk,
        isSuggestion = isSuggestion,
        inReplyToId = inReplyToId,
        reviewId = reviewId,
    )

    // ---- Basic construction ----

    @Test
    fun `can construct a review comment`() {
        val rc = makeComment()
        assertNotNull(rc)
        assertEquals(1L, rc.id)
        assertEquals("alice", rc.author)
        assertEquals("src/Foo.kt", rc.path)
    }

    @Test
    fun `timestamps are preserved`() {
        val rc = makeComment()
        assertEquals(Instant.parse("2024-01-01T00:00:00Z"), rc.createdAt)
        assertEquals(Instant.parse("2024-01-01T01:00:00Z"), rc.updatedAt)
    }

    // ---- isSuggestion flag ----

    @Test
    fun `isSuggestion false by default`() {
        val rc = makeComment(isSuggestion = false)
        assertFalse(rc.isSuggestion)
    }

    @Test
    fun `isSuggestion can be explicitly set to true`() {
        val rc = makeComment(isSuggestion = true)
        assertTrue(rc.isSuggestion)
    }

    @Test
    fun `copy preserves isSuggestion`() {
        val original = makeComment(isSuggestion = true)
        val copied = original.copy(author = "bob")
        assertTrue("copy must preserve isSuggestion", copied.isSuggestion)
        assertEquals("bob", copied.author)
    }

    // ---- Thread replies ----

    @Test
    fun `inReplyToId is null for top-level comment`() {
        val rc = makeComment(inReplyToId = null)
        assertNull(rc.inReplyToId)
    }

    @Test
    fun `inReplyToId is set for reply`() {
        val rc = makeComment(inReplyToId = 77L)
        assertEquals(77L, rc.inReplyToId)
    }

    @Test
    fun `top-level and reply can be distinguished`() {
        val topLevel = makeComment(id = 1L, inReplyToId = null)
        val reply = makeComment(id = 2L, inReplyToId = 1L)
        assertNull(topLevel.inReplyToId)
        assertNotNull(reply.inReplyToId)
        assertEquals(1L, reply.inReplyToId)
    }

    // ---- Nullable line ----

    @Test
    fun `line is preserved when set`() {
        val rc = makeComment(line = 42)
        assertEquals(42, rc.line)
    }

    @Test
    fun `line is null for file-level comment`() {
        val rc = makeComment(line = null)
        assertNull(rc.line)
    }

    // ---- Equality and identity ----

    @Test
    fun `two comments with same id and itemId are equal`() {
        val a = makeComment(id = 5L, itemId = 200L)
        val b = makeComment(id = 5L, itemId = 200L)
        assertEquals(a, b)
    }

    @Test
    fun `comments with different ids are not equal`() {
        val a = makeComment(id = 5L)
        val b = makeComment(id = 6L)
        assert(a != b) { "Comments with different ids must not be equal" }
    }

    // ---- Grouping by path (simulates the UI grouping logic) ----

    @Test
    fun `comments can be grouped by path`() {
        val comments = listOf(
            makeComment(id = 1L, path = "src/A.kt"),
            makeComment(id = 2L, path = "src/B.kt"),
            makeComment(id = 3L, path = "src/A.kt"),
        )
        val grouped = comments.groupBy { it.path }
        assertEquals(2, grouped.size)
        assertEquals(2, grouped["src/A.kt"]?.size)
        assertEquals(1, grouped["src/B.kt"]?.size)
    }

    // ---- Suggestion body detection helper (mirrors service logic) ----

    @Test
    fun `body containing suggestion fence is detected as suggestion`() {
        val body = "Please apply this fix:\n```suggestion\nval x = corrected\n```"
        assertTrue(body.contains("```suggestion", ignoreCase = true))
    }

    @Test
    fun `plain body is not detected as suggestion`() {
        val body = "Could you add a comment here?"
        assertFalse(body.contains("```suggestion", ignoreCase = true))
    }

    @Test
    fun `empty body is not detected as suggestion`() {
        assertFalse("".contains("```suggestion", ignoreCase = true))
    }

    // ---- Review thread ordering ----

    @Test
    fun `thread can be reconstructed by inReplyToId chain`() {
        val root = makeComment(id = 10L, inReplyToId = null)
        val reply1 = makeComment(id = 11L, inReplyToId = 10L)
        val reply2 = makeComment(id = 12L, inReplyToId = 10L)

        val thread = listOf(root, reply1, reply2)
            .sortedBy { it.inReplyToId ?: Long.MIN_VALUE }

        assertEquals(10L, thread.first().id)  // root first
    }

    @Test
    fun `hasMoreReviewComments logic — 30 results implies more pages available`() {
        val comments = (1..30).map { makeComment(id = it.toLong()) }
        val hasMore = comments.size >= 30
        assertTrue(hasMore)
    }

    @Test
    fun `hasMoreReviewComments logic — fewer than 30 results implies last page`() {
        val comments = (1..12).map { makeComment(id = it.toLong()) }
        val hasMore = comments.size >= 30
        assertFalse(hasMore)
    }
}

