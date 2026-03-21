package io.orbitrack.idea.model

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

