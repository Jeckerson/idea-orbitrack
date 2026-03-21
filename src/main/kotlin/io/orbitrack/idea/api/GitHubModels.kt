package io.orbitrack.idea.api

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

