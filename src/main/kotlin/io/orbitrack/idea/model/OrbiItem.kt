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

