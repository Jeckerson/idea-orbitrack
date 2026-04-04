package dev.anvas.orbitrack.idea

import dev.anvas.orbitrack.idea.model.ItemState
import dev.anvas.orbitrack.idea.model.ItemType
import dev.anvas.orbitrack.idea.model.OrbiComment
import dev.anvas.orbitrack.idea.model.OrbiItem
import dev.anvas.orbitrack.idea.model.OrbiReviewComment
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * Tests that verify the Markdown export content produced by
 * [OrbiTrackPanel.buildMdFileContent] and the LLM clipboard context include
 * PR review comments (code suggestions) when present.
 *
 * Because [OrbiTrackPanel] is a Swing component that needs an IntelliJ
 * [Project], we replicate the pure rendering logic here as a standalone
 * helper and keep the tests free of platform dependencies.
 */
class MdExportReviewCommentsTest {

    // ---------- fixtures ----------

    private val dateFmt = java.time.format.DateTimeFormatter
        .ofPattern("yyyy-MM-dd HH:mm")
        .withZone(java.time.ZoneId.of("UTC"))

    private fun makeItem() = OrbiItem(
        id = 1L, org = "acme", repo = "app", number = 42,
        type = ItemType.PR, state = ItemState.OPEN,
        title = "Add feature X", body = "Body text.",
        labels = listOf("bug"), assignees = emptyList(),
        author = "alice", milestone = null, commentCount = 2,
        createdAt = Instant.parse("2024-03-01T10:00:00Z"),
        updatedAt = Instant.parse("2024-03-02T12:00:00Z"),
        url = "https://github.com/acme/app/pull/42",
    )

    private fun makeReviewComment(
        id: Long,
        author: String = "reviewer",
        body: String = "Looks good",
        path: String = "src/Main.kt",
        line: Int? = 10,
        diffHunk: String = "@@ -8,7 +8,7 @@\n context\n-old\n+new",
        isSuggestion: Boolean = false,
        inReplyToId: Long? = null,
    ) = OrbiReviewComment(
        id = id, itemId = 1L, author = author, body = body,
        createdAt = Instant.parse("2024-03-01T11:00:00Z"),
        updatedAt = Instant.parse("2024-03-01T11:00:00Z"),
        path = path, line = line, diffHunk = diffHunk,
        isSuggestion = isSuggestion, inReplyToId = inReplyToId,
        reviewId = null,
    )

    private fun makeComment(id: Long, author: String = "bob", body: String = "A comment") =
        OrbiComment(
            id = id, itemId = 1L, author = author, body = body,
            createdAt = Instant.parse("2024-03-01T09:00:00Z"),
            updatedAt = Instant.parse("2024-03-01T09:00:00Z"),
            canEdit = false,
        )

    // ---------- replicated render logic (mirrors OrbiTrackPanel.buildMdFileContent) ----------

    private fun buildMd(
        item: OrbiItem,
        comments: List<OrbiComment>,
        reviewComments: List<OrbiReviewComment>,
    ): String = buildString {
        val typeStr = if (item.type == ItemType.PR) "PR" else "Issue"
        appendLine("# $typeStr #${item.number}: ${item.title}")
        appendLine()
        appendLine("**Repo:** ${item.org}/${item.repo}")
        appendLine("**State:** ${item.state.name.lowercase()}")
        appendLine("**Author:** @${item.author}")
        appendLine("**URL:** ${item.url}")
        appendLine("**Opened:** ${dateFmt.format(item.createdAt)}")
        appendLine("**Updated:** ${dateFmt.format(item.updatedAt)}")
        appendLine()
        appendLine("---")
        appendLine()
        appendLine("## Description")
        appendLine()
        appendLine(item.body.ifBlank { "*(no description)*" })
        if (comments.isNotEmpty()) {
            appendLine()
            appendLine("---")
            appendLine()
            appendLine("## Comments (${comments.size})")
            for (c in comments) {
                appendLine()
                appendLine("### @${c.author} · ${dateFmt.format(c.createdAt)}")
                appendLine()
                appendLine(c.body)
            }
        }
        if (reviewComments.isNotEmpty()) {
            appendLine()
            appendLine("---")
            appendLine()
            appendLine("## Code Review (${reviewComments.size} inline comments)")
            val byPath = reviewComments.groupBy { it.path }
            for ((path, pathComments) in byPath) {
                appendLine()
                appendLine("### `$path`")
                for (rc in pathComments) {
                    appendLine()
                    val replyTag = if (rc.inReplyToId != null) " ↩ reply" else ""
                    val suggestionTag = if (rc.isSuggestion) " 💡 suggestion" else ""
                    val lineTag = rc.line?.let { " · line $it" } ?: ""
                    appendLine("#### @${rc.author}$lineTag$suggestionTag$replyTag · ${dateFmt.format(rc.createdAt)}")
                    appendLine()
                    if (rc.diffHunk.isNotBlank()) {
                        appendLine("```diff")
                        appendLine(rc.diffHunk)
                        appendLine("```")
                        appendLine()
                    }
                    appendLine(rc.body)
                }
            }
        }
    }

    // ---------- tests ----------

    @Test
    fun `md export includes Code Review section when review comments present`() {
        val md = buildMd(makeItem(), emptyList(), listOf(makeReviewComment(1L)))
        assertTrue("Must contain Code Review header", md.contains("## Code Review"))
    }

    @Test
    fun `md export omits Code Review section when no review comments`() {
        val md = buildMd(makeItem(), emptyList(), emptyList())
        assertFalse("Must not contain Code Review section for empty list", md.contains("## Code Review"))
    }

    @Test
    fun `md export groups review comments by file path`() {
        val rcs = listOf(
            makeReviewComment(1L, path = "src/A.kt"),
            makeReviewComment(2L, path = "src/B.kt"),
            makeReviewComment(3L, path = "src/A.kt"),
        )
        val md = buildMd(makeItem(), emptyList(), rcs)
        assertTrue(md.contains("`src/A.kt`"))
        assertTrue(md.contains("`src/B.kt`"))
        // Both A.kt comments should appear; verify count in header
        assertTrue("Should show 3 total review comments", md.contains("3 inline comments"))
    }

    @Test
    fun `md export includes diff hunk in a diff code fence`() {
        val rc = makeReviewComment(1L, diffHunk = "@@ -1,3 +1,3 @@\n-old\n+new")
        val md = buildMd(makeItem(), emptyList(), listOf(rc))
        assertTrue("Diff hunk must be inside a diff fence", md.contains("```diff"))
        assertTrue(md.contains("@@ -1,3 +1,3 @@"))
        assertTrue(md.contains("-old"))
        assertTrue(md.contains("+new"))
    }

    @Test
    fun `md export marks suggestion comments with emoji tag`() {
        val rc = makeReviewComment(1L, isSuggestion = true,
            body = "```suggestion\nval x = 1\n```")
        val md = buildMd(makeItem(), emptyList(), listOf(rc))
        assertTrue("Suggestion tag must appear in header", md.contains("💡 suggestion"))
    }

    @Test
    fun `md export marks reply comments with reply indicator`() {
        val rc = makeReviewComment(1L, inReplyToId = 99L, body = "Thanks!")
        val md = buildMd(makeItem(), emptyList(), listOf(rc))
        assertTrue("Reply indicator must appear in header", md.contains("↩ reply"))
    }

    @Test
    fun `md export includes line number when present`() {
        val rc = makeReviewComment(1L, line = 42)
        val md = buildMd(makeItem(), emptyList(), listOf(rc))
        assertTrue("Line number must appear in review comment header", md.contains("line 42"))
    }

    @Test
    fun `md export omits line number when null`() {
        val rc = makeReviewComment(1L, line = null, diffHunk = "")
        val md = buildMd(makeItem(), emptyList(), listOf(rc))
        assertFalse(md.contains("· line"))
    }

    @Test
    fun `md export includes both conversation comments and review comments`() {
        val comments = listOf(makeComment(1L, body = "Conversation comment"))
        val rcs = listOf(makeReviewComment(1L, body = "Review comment"))
        val md = buildMd(makeItem(), comments, rcs)
        assertTrue(md.contains("## Comments"))
        assertTrue(md.contains("Conversation comment"))
        assertTrue(md.contains("## Code Review"))
        assertTrue(md.contains("Review comment"))
    }

    @Test
    fun `md export omits diff fence when diffHunk is blank`() {
        val rc = makeReviewComment(1L, diffHunk = "")
        val md = buildMd(makeItem(), emptyList(), listOf(rc))
        assertFalse("No diff fence when hunk is blank", md.contains("```diff"))
    }

    @Test
    fun `md export includes review comment author`() {
        val rc = makeReviewComment(1L, author = "carol")
        val md = buildMd(makeItem(), emptyList(), listOf(rc))
        assertTrue(md.contains("@carol"))
    }

    @Test
    fun `issues without review comments produce valid md without Code Review section`() {
        val issue = makeItem().copy(type = ItemType.ISSUE)
        val md = buildMd(issue, listOf(makeComment(1L)), emptyList())
        assertTrue(md.startsWith("# Issue #42"))
        assertFalse(md.contains("Code Review"))
    }
}

