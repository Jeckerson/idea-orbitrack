package io.orbitrack.idea.api

import io.orbitrack.idea.model.OrbiComment
import io.orbitrack.idea.model.OrbiItem
import io.orbitrack.idea.model.OrbiTimelineEvent
import java.time.Instant

interface GitHubClient {
    suspend fun listIssues(org: String, repo: String, state: String = "open", perPage: Int = 20, page: Int = 1): List<OrbiItem>
    suspend fun listPRs(org: String, repo: String, state: String = "open", perPage: Int = 20, page: Int = 1): List<OrbiItem>
    suspend fun getComments(org: String, repo: String, number: Int, perPage: Int = 30, page: Int = 1): List<OrbiComment>
    suspend fun createComment(org: String, repo: String, number: Int, body: String): OrbiComment
    suspend fun updateComment(org: String, repo: String, commentId: Long, body: String): OrbiComment
    suspend fun deleteComment(org: String, repo: String, commentId: Long)
    suspend fun listOrgs(): List<String>
    suspend fun listRepos(org: String): List<String>
    suspend fun getAuthenticatedUser(): String?

    /** Batch search for issues/PRs updated since [since] across multiple repos. */
    suspend fun searchUpdatedItems(repos: List<Pair<String, String>>, since: Instant, perPage: Int = 100, page: Int = 1): List<OrbiItem>

    /** Create a new issue in the given repo. */
    suspend fun createIssue(org: String, repo: String, title: String, body: String, labels: List<String> = emptyList(), assignees: List<String> = emptyList()): OrbiItem

    /** Fetch timeline events for an issue/PR. */
    suspend fun getTimeline(org: String, repo: String, number: Int, perPage: Int = 100, page: Int = 1): List<OrbiTimelineEvent>

    /** Fetch detailed PR info including mergeability and branch refs. */
    suspend fun getPullDetail(org: String, repo: String, number: Int): GhPullDetail

    /** Merge a pull request. Returns the merge result. */
    suspend fun mergePull(
        org: String,
        repo: String,
        number: Int,
        mergeMethod: String = "merge",        // "merge" | "squash" | "rebase"
        commitTitle: String? = null,
        commitMessage: String? = null,
        sha: String? = null,
    ): GhMergeResult
}

