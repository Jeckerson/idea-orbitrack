package dev.anvas.orbitrack.idea

import dev.anvas.orbitrack.idea.api.GhReviewComment
import dev.anvas.orbitrack.idea.api.GhUser
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure unit tests for [GhReviewComment] DTO — JSON deserialization and domain mapping.
 * No IntelliJ Platform or `gh` CLI required.
 */
class GhReviewCommentTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    // ---- JSON deserialization ----

    @Test
    fun `deserializes minimal review comment`() {
        val raw = """{
            "id": 1,
            "user": {"login": "alice"},
            "body": "LGTM",
            "created_at": "2024-01-01T00:00:00Z",
            "updated_at": "2024-01-01T00:00:00Z",
            "path": "src/Foo.kt",
            "diff_hunk": "@@ -1,3 +1,3 @@"
        }"""
        val comment = json.decodeFromString<GhReviewComment>(raw)
        assertEquals(1L, comment.id)
        assertEquals("alice", comment.user.login)
        assertEquals("LGTM", comment.body)
        assertEquals("src/Foo.kt", comment.path)
        assertEquals("@@ -1,3 +1,3 @@", comment.diffHunk)
        assertNull(comment.line)
        assertNull(comment.inReplyToId)
        assertNull(comment.pullRequestReviewId)
    }

    @Test
    fun `deserializes review comment with all optional fields`() {
        val raw = """{
            "id": 42,
            "user": {"login": "bob"},
            "body": "```suggestion\nfixed\n```",
            "created_at": "2024-06-15T12:00:00Z",
            "updated_at": "2024-06-15T12:30:00Z",
            "path": "lib/Bar.kt",
            "line": 17,
            "original_line": 15,
            "diff_hunk": "@@ -14,6 +14,6 @@",
            "in_reply_to_id": 7,
            "pull_request_review_id": 99
        }"""
        val comment = json.decodeFromString<GhReviewComment>(raw)
        assertEquals(42L, comment.id)
        assertEquals(17, comment.line)
        assertEquals(15, comment.originalLine)
        assertEquals(7L, comment.inReplyToId)
        assertEquals(99L, comment.pullRequestReviewId)
    }

    @Test
    fun `deserializes review comment with null body`() {
        val raw = """{
            "id": 5,
            "user": {"login": "carol"},
            "body": null,
            "created_at": "2024-01-01T00:00:00Z",
            "updated_at": "2024-01-01T00:00:00Z",
            "path": "README.md",
            "diff_hunk": "@@ -1 +1 @@"
        }"""
        val comment = json.decodeFromString<GhReviewComment>(raw)
        assertNull(comment.body)
        // mapping converts null to empty string
        val mapped = comment.toOrbiReviewComment(itemId = 0L)
        assertEquals("", mapped.body)
    }

    @Test
    fun `ignores unknown fields from GitHub API`() {
        val raw = """{
            "id": 3,
            "user": {"login": "dave", "avatar_url": "https://example.com/avatar.png"},
            "body": "nit",
            "created_at": "2024-01-01T00:00:00Z",
            "updated_at": "2024-01-01T00:00:00Z",
            "path": "main.kt",
            "diff_hunk": "@@ -1 +1 @@",
            "url": "https://api.github.com/...",
            "html_url": "https://github.com/...",
            "commit_id": "abc123",
            "position": null,
            "original_position": 3,
            "subject_type": "line"
        }"""
        val comment = json.decodeFromString<GhReviewComment>(raw)
        assertEquals(3L, comment.id)
        assertEquals("nit", comment.body)
    }

    // ---- toOrbiReviewComment mapping ----

    @Test
    fun `toOrbiReviewComment maps all fields correctly`() {
        val gh = GhReviewComment(
            id = 10L,
            user = GhUser("eve"),
            body = "Looks good",
            createdAt = "2024-03-01T08:00:00Z",
            updatedAt = "2024-03-01T09:00:00Z",
            path = "src/Main.kt",
            line = 42,
            originalLine = 40,
            diffHunk = "@@ -40,4 +40,4 @@\n context\n-old\n+new",
            inReplyToId = null,
            pullRequestReviewId = 55L,
        )
        val mapped = gh.toOrbiReviewComment(itemId = 999L)

        assertEquals(10L, mapped.id)
        assertEquals(999L, mapped.itemId)
        assertEquals("eve", mapped.author)
        assertEquals("Looks good", mapped.body)
        assertEquals("src/Main.kt", mapped.path)
        assertEquals(42, mapped.line)
        assertEquals("@@ -40,4 +40,4 @@\n context\n-old\n+new", mapped.diffHunk)
        assertFalse(mapped.isSuggestion)
        assertNull(mapped.inReplyToId)
        assertEquals(55L, mapped.reviewId)
    }

    @Test
    fun `toOrbiReviewComment falls back to originalLine when line is null`() {
        val gh = GhReviewComment(
            id = 1L,
            user = GhUser("frank"),
            body = "comment",
            createdAt = "2024-01-01T00:00:00Z",
            updatedAt = "2024-01-01T00:00:00Z",
            path = "a.kt",
            line = null,
            originalLine = 7,
            diffHunk = "",
        )
        val mapped = gh.toOrbiReviewComment(0L)
        assertEquals(7, mapped.line)
    }

    @Test
    fun `toOrbiReviewComment has null line when both line fields are null`() {
        val gh = GhReviewComment(
            id = 2L,
            user = GhUser("grace"),
            body = "",
            createdAt = "2024-01-01T00:00:00Z",
            updatedAt = "2024-01-01T00:00:00Z",
            path = "b.kt",
            diffHunk = "",
        )
        val mapped = gh.toOrbiReviewComment(0L)
        assertNull(mapped.line)
    }

    @Test
    fun `toOrbiReviewComment sets inReplyToId when present`() {
        val gh = GhReviewComment(
            id = 3L,
            user = GhUser("hank"),
            body = "agreed",
            createdAt = "2024-01-01T00:00:00Z",
            updatedAt = "2024-01-01T00:00:00Z",
            path = "c.kt",
            diffHunk = "",
            inReplyToId = 88L,
        )
        val mapped = gh.toOrbiReviewComment(0L)
        assertEquals(88L, mapped.inReplyToId)
    }

    // ---- isSuggestion detection ----

    @Test
    fun `isSuggestion is true when body contains suggestion fence`() {
        val gh = GhReviewComment(
            id = 4L,
            user = GhUser("ivy"),
            body = "Here's a fix:\n```suggestion\nval x = 1\n```",
            createdAt = "2024-01-01T00:00:00Z",
            updatedAt = "2024-01-01T00:00:00Z",
            path = "d.kt",
            diffHunk = "",
        )
        val mapped = gh.toOrbiReviewComment(0L)
        assertTrue("isSuggestion must be true when body contains ```suggestion", mapped.isSuggestion)
    }

    @Test
    fun `isSuggestion is true regardless of case`() {
        val gh = GhReviewComment(
            id = 5L,
            user = GhUser("jack"),
            body = "```Suggestion\nfixed\n```",
            createdAt = "2024-01-01T00:00:00Z",
            updatedAt = "2024-01-01T00:00:00Z",
            path = "e.kt",
            diffHunk = "",
        )
        assertTrue(gh.toOrbiReviewComment(0L).isSuggestion)
    }

    @Test
    fun `isSuggestion is false for plain comment`() {
        val gh = GhReviewComment(
            id = 6L,
            user = GhUser("kate"),
            body = "Please rename this variable.",
            createdAt = "2024-01-01T00:00:00Z",
            updatedAt = "2024-01-01T00:00:00Z",
            path = "f.kt",
            diffHunk = "",
        )
        assertFalse(gh.toOrbiReviewComment(0L).isSuggestion)
    }

    @Test
    fun `isSuggestion is false for null body`() {
        val gh = GhReviewComment(
            id = 7L,
            user = GhUser("leo"),
            body = null,
            createdAt = "2024-01-01T00:00:00Z",
            updatedAt = "2024-01-01T00:00:00Z",
            path = "g.kt",
            diffHunk = "",
        )
        assertFalse(gh.toOrbiReviewComment(0L).isSuggestion)
    }

    // ---- Timestamps ----

    @Test
    fun `toOrbiReviewComment parses ISO-8601 timestamps`() {
        val gh = GhReviewComment(
            id = 8L,
            user = GhUser("mia"),
            body = "ts test",
            createdAt = "2024-07-04T14:30:00Z",
            updatedAt = "2024-07-04T15:00:00Z",
            path = "h.kt",
            diffHunk = "",
        )
        val mapped = gh.toOrbiReviewComment(0L)
        assertEquals(1720103400000L, mapped.createdAt.toEpochMilli())
        assertEquals(1720105200000L, mapped.updatedAt.toEpochMilli())
    }
}

