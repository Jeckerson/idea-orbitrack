package dev.anvas.orbitrack.idea.model

import kotlinx.serialization.Serializable
import java.time.Instant

enum class ItemType { ISSUE, PR }

enum class ItemState { OPEN, CLOSED, MERGED }

data class OrbiItem(
    val id: Long,
    val org: String,
    val repo: String,
    val number: Int,
    val type: ItemType,
    val state: ItemState,
    val title: String,
    val body: String,
    val labels: List<String>,
    val assignees: List<String>,
    val author: String,
    val milestone: String?,
    val commentCount: Int,
    val createdAt: Instant,
    val updatedAt: Instant,
    val url: String,
    // PR-specific fields (populated lazily via getPullDetail)
    val headBranch: String? = null,
    val baseBranch: String? = null,
    val headSha: String? = null,
    val isFork: Boolean = false,
    val mergeable: Boolean? = null,       // null = unknown/checking, true = clean, false = conflicts
    val mergeableState: String? = null,   // "clean", "dirty", "unstable", "blocked", "unknown"
)

data class OrbiComment(
    val id: Long,
    val itemId: Long,
    val author: String,
    val body: String,
    val createdAt: Instant,
    val updatedAt: Instant,
    val canEdit: Boolean,
)

/**
 * A PR inline review comment (from the "Files changed" tab).
 * May contain a GitHub code suggestion block (```suggestion).
 */
data class OrbiReviewComment(
    val id: Long,
    val itemId: Long,          // the PR id
    val author: String,
    val body: String,
    val createdAt: Instant,
    val updatedAt: Instant,
    val path: String,          // file path the comment is anchored to
    val line: Int?,            // target line number (null for file-level comments)
    val diffHunk: String,      // surrounding diff context sent by GitHub
    val isSuggestion: Boolean, // body contains a ```suggestion block
    val inReplyToId: Long?,    // non-null when this is a reply inside a thread
    val reviewId: Long?,       // pull_request_review_id
)

@Serializable
data class TrackedRepo(
    val org: String,
    val repo: String,       // "*" means all repos in org
    val enabled: Boolean,
)

data class OrbiTimelineEvent(
    val type: String,        // e.g. "labeled", "closed", "assigned", "renamed", etc.
    val actor: String?,
    val detail: String,      // human-readable detail, e.g. "added label bug"
    val timestamp: Instant,
)

