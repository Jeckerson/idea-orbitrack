package dev.anvas.orbitrack.idea.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GhUser(
    val login: String,
)

@Serializable
data class GhLabel(
    val name: String,
)

@Serializable
data class GhMilestone(
    val title: String,
)

@Serializable
data class GhPullRequestRef(
    val url: String? = null,
)

@Serializable
data class GhBranchRef(
    val ref: String = "",
    val sha: String = "",
    val label: String = "",
    val repo: GhBranchRepo? = null,
)

@Serializable
data class GhBranchRepo(
    @SerialName("full_name") val fullName: String = "",
    val fork: Boolean = false,
)

@Serializable
data class GhPullDetail(
    val id: Long,
    val number: Int,
    val title: String,
    val body: String? = null,
    val state: String,
    val merged: Boolean = false,
    val mergeable: Boolean? = null,
    @SerialName("mergeable_state") val mergeableState: String? = null,
    val head: GhBranchRef = GhBranchRef(),
    val base: GhBranchRef = GhBranchRef(),
    val user: GhUser,
    val labels: List<GhLabel> = emptyList(),
    val assignees: List<GhUser> = emptyList(),
    val milestone: GhMilestone? = null,
    val comments: Int = 0,
    @SerialName("review_comments") val reviewComments: Int = 0,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("html_url") val htmlUrl: String,
)

@Serializable
data class GhMergeResult(
    val sha: String? = null,
    val merged: Boolean = false,
    val message: String = "",
)

@Serializable
data class GhIssue(
    val id: Long,
    val number: Int,
    val title: String,
    val body: String? = null,
    val state: String,               // "open" | "closed"
    val user: GhUser,
    val labels: List<GhLabel> = emptyList(),
    val assignees: List<GhUser> = emptyList(),
    val milestone: GhMilestone? = null,
    val comments: Int = 0,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("html_url") val htmlUrl: String,
    @SerialName("pull_request") val pullRequest: GhPullRequestRef? = null,
)

@Serializable
data class GhPull(
    val id: Long,
    val number: Int,
    val title: String,
    val body: String? = null,
    val state: String,
    val merged: Boolean = false,
    val user: GhUser,
    val labels: List<GhLabel> = emptyList(),
    val assignees: List<GhUser> = emptyList(),
    val milestone: GhMilestone? = null,
    val comments: Int = 0,
    @SerialName("review_comments") val reviewComments: Int = 0,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("html_url") val htmlUrl: String,
)

@Serializable
data class GhComment(
    val id: Long,
    val user: GhUser,
    val body: String? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
)

@Serializable
data class GhSearchResult(
    @SerialName("total_count") val totalCount: Int,
    @SerialName("incomplete_results") val incompleteResults: Boolean = false,
    val items: List<GhIssue> = emptyList(),
)

@Serializable
data class GhRename(
    val from: String = "",
    val to: String = "",
)

@Serializable
data class GhTimelineEvent(
    val event: String = "",                            // "labeled", "closed", "assigned", "renamed", etc.
    val actor: GhUser? = null,
    val label: GhLabel? = null,
    val assignee: GhUser? = null,
    val milestone: GhMilestone? = null,
    val rename: GhRename? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

