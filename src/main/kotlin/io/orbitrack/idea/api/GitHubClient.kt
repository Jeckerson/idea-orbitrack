package io.orbitrack.idea.api

import io.orbitrack.idea.model.OrbiComment
import io.orbitrack.idea.model.OrbiItem

interface GitHubClient {
    suspend fun listIssues(org: String, repo: String, state: String = "open", perPage: Int = 20, page: Int = 1): List<OrbiItem>
    suspend fun listPRs(org: String, repo: String, state: String = "open", perPage: Int = 20, page: Int = 1): List<OrbiItem>
    suspend fun getComments(org: String, repo: String, number: Int, perPage: Int = 30, page: Int = 1): List<OrbiComment>
    suspend fun createComment(org: String, repo: String, number: Int, body: String): OrbiComment
    suspend fun updateComment(org: String, repo: String, commentId: Long, body: String): OrbiComment
    suspend fun deleteComment(org: String, repo: String, commentId: Long)
    suspend fun listOrgs(): List<String>
    suspend fun listRepos(org: String): List<String>
}

