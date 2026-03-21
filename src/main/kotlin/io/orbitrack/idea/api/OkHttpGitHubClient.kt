package io.orbitrack.idea.api

import com.intellij.openapi.diagnostic.Logger
import io.orbitrack.idea.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

class OkHttpGitHubClient(private val token: String) : GitHubClient {

    private val log = Logger.getInstance(OkHttpGitHubClient::class.java)

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val baseUrl = "https://api.github.com"
    private val jsonMedia = "application/json".toMediaType()

    // ---- Issues ----

    override suspend fun listIssues(org: String, repo: String, state: String, perPage: Int, page: Int): List<OrbiItem> {
        val body = get("$baseUrl/repos/$org/$repo/issues?state=$state&per_page=$perPage&page=$page&sort=updated&direction=desc")
        val issues = json.decodeFromString<List<GhIssue>>(body)
        return issues
            .filter { it.pullRequest == null }  // GitHub mixes PRs into issues endpoint
            .map { it.toOrbiItem(org, repo) }
    }

    override suspend fun listPRs(org: String, repo: String, state: String, perPage: Int, page: Int): List<OrbiItem> {
        val body = get("$baseUrl/repos/$org/$repo/pulls?state=$state&per_page=$perPage&page=$page&sort=updated&direction=desc")
        val pulls = json.decodeFromString<List<GhPull>>(body)
        return pulls.map { it.toOrbiItem(org, repo) }
    }

    // ---- Comments ----

    override suspend fun getComments(org: String, repo: String, number: Int, perPage: Int, page: Int): List<OrbiComment> {
        val body = get("$baseUrl/repos/$org/$repo/issues/$number/comments?per_page=$perPage&page=$page")
        val comments = json.decodeFromString<List<GhComment>>(body)
        return comments.map { it.toOrbiComment(0L) }
    }

    override suspend fun createComment(org: String, repo: String, number: Int, body: String): OrbiComment {
        val payload = """{"body":${json.encodeToString(kotlinx.serialization.serializer<String>(), body)}}"""
        val response = post("$baseUrl/repos/$org/$repo/issues/$number/comments", payload)
        val ghComment = json.decodeFromString<GhComment>(response)
        return ghComment.toOrbiComment(0L)
    }

    override suspend fun updateComment(org: String, repo: String, commentId: Long, body: String): OrbiComment {
        val payload = """{"body":${json.encodeToString(kotlinx.serialization.serializer<String>(), body)}}"""
        val response = patch("$baseUrl/repos/$org/$repo/issues/comments/$commentId", payload)
        val ghComment = json.decodeFromString<GhComment>(response)
        return ghComment.toOrbiComment(0L)
    }

    override suspend fun deleteComment(org: String, repo: String, commentId: Long) {
        delete("$baseUrl/repos/$org/$repo/issues/comments/$commentId")
    }

    // ---- Orgs / Repos ----

    override suspend fun listOrgs(): List<String> {
        val body = get("$baseUrl/user/orgs?per_page=100")
        @kotlinx.serialization.Serializable
        data class OrgItem(val login: String)
        return json.decodeFromString<List<OrgItem>>(body).map { it.login }
    }

    override suspend fun listRepos(org: String): List<String> {
        val body = get("$baseUrl/orgs/$org/repos?per_page=100&sort=updated")
        @kotlinx.serialization.Serializable
        data class RepoItem(val name: String)
        return json.decodeFromString<List<RepoItem>>(body).map { it.name }
    }

    // ---- Authenticated user login (for canEdit checks) ----

    override suspend fun getAuthenticatedUser(): String? {
        return try {
            val body = get("$baseUrl/user")
            @kotlinx.serialization.Serializable
            data class UserInfo(val login: String)
            json.decodeFromString<UserInfo>(body).login
        } catch (e: Exception) {
            log.warn("Failed to get authenticated user", e)
            null
        }
    }

    // ---- Batched search (incremental refresh) ----

    override suspend fun searchUpdatedItems(
        repos: List<Pair<String, String>>,
        since: Instant,
        perPage: Int,
        page: Int
    ): List<OrbiItem> {
        if (repos.isEmpty()) return emptyList()
        val sinceStr = DateTimeFormatter.ISO_INSTANT.format(since)
        // Build query: repo:org1/repo1+repo:org2/repo2+updated:>2024-01-01T00:00:00Z
        val repoQ = repos.joinToString("+") { (o, r) -> "repo:$o/$r" }
        val query = URLEncoder.encode("$repoQ updated:>$sinceStr", Charsets.UTF_8)
        val url = "$baseUrl/search/issues?q=$query&per_page=$perPage&page=$page&sort=updated&order=desc"
        val body = get(url)
        val result = json.decodeFromString<GhSearchResult>(body)
        return result.items.map { issue ->
            // Extract org/repo from html_url: https://github.com/{org}/{repo}/issues/{num}
            val parts = issue.htmlUrl.removePrefix("https://github.com/").split("/")
            val org = parts.getOrElse(0) { "" }
            val repo = parts.getOrElse(1) { "" }
            val isPr = issue.pullRequest != null
            if (isPr) {
                issue.toOrbiPrItem(org, repo)
            } else {
                issue.toOrbiItem(org, repo)
            }
        }
    }

    // ---- Create Issue ----

    override suspend fun createIssue(
        org: String,
        repo: String,
        title: String,
        body: String,
        labels: List<String>,
        assignees: List<String>
    ): OrbiItem {
        val payload = buildString {
            append("{")
            append("\"title\":${json.encodeToString(serializer<String>(), title)}")
            append(",\"body\":${json.encodeToString(serializer<String>(), body)}")
            if (labels.isNotEmpty()) {
                append(",\"labels\":${json.encodeToString(serializer<List<String>>(), labels)}")
            }
            if (assignees.isNotEmpty()) {
                append(",\"assignees\":${json.encodeToString(serializer<List<String>>(), assignees)}")
            }
            append("}")
        }
        val response = post("$baseUrl/repos/$org/$repo/issues", payload)
        val issue = json.decodeFromString<GhIssue>(response)
        return issue.toOrbiItem(org, repo)
    }

    // ---- Timeline ----

    override suspend fun getTimeline(
        org: String,
        repo: String,
        number: Int,
        perPage: Int,
        page: Int
    ): List<OrbiTimelineEvent> {
        val body = get("$baseUrl/repos/$org/$repo/issues/$number/timeline?per_page=$perPage&page=$page")
        val events = json.decodeFromString<List<GhTimelineEvent>>(body)
        return events.mapNotNull { it.toOrbiTimelineEvent() }
    }

    // ---- HTTP helpers ----

    private suspend fun get(url: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .build()
        executeWithRateLimitRetry(request).use { response ->
            response.body?.string() ?: "[]"
        }
    }

    private suspend fun post(url: String, jsonBody: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .post(jsonBody.toRequestBody(jsonMedia))
            .build()
        executeWithRateLimitRetry(request).use { response ->
            response.body?.string() ?: "{}"
        }
    }

    private suspend fun patch(url: String, jsonBody: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .patch(jsonBody.toRequestBody(jsonMedia))
            .build()
        executeWithRateLimitRetry(request).use { response ->
            response.body?.string() ?: "{}"
        }
    }

    private suspend fun delete(url: String): Unit = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .delete()
            .build()
        executeWithRateLimitRetry(request).use { }
    }

    /**
     * Executes a request with automatic retry on 429 / 403 secondary rate limit.
     * Respects `Retry-After` and `X-RateLimit-Reset` headers.
     */
    private suspend fun executeWithRateLimitRetry(
        request: Request,
        maxRetries: Int = 3,
    ): okhttp3.Response {
        var lastResponse: okhttp3.Response? = null
        for (attempt in 0..maxRetries) {
            val response = http.newCall(request).execute()
            if (response.isSuccessful) return response

            val code = response.code
            val body = response.body?.string() ?: ""
            response.close()

            val isRateLimit = code == 429 || (code == 403 && body.contains("rate limit", ignoreCase = true))
            if (!isRateLimit || attempt == maxRetries) {
                throw GitHubApiException(code, "${request.method} ${request.url} failed: $code $body")
            }

            // Determine wait time
            val retryAfter = response.header("Retry-After")?.toLongOrNull()
            val resetEpoch = response.header("X-RateLimit-Reset")?.toLongOrNull()
            val waitSeconds = when {
                retryAfter != null -> retryAfter
                resetEpoch != null -> maxOf(resetEpoch - (System.currentTimeMillis() / 1000), 1L)
                else -> (1L shl attempt) * 10  // exponential: 10s, 20s, 40s
            }.coerceAtMost(120)

            log.info("Rate limited (${code}), retrying in ${waitSeconds}s (attempt ${attempt + 1}/$maxRetries)")
            kotlinx.coroutines.delay(waitSeconds * 1000)
        }
        throw GitHubApiException(429, "Exhausted rate-limit retries for ${request.url}")
    }

    // ---- Mapping helpers ----

    private fun GhIssue.toOrbiItem(org: String, repo: String) = OrbiItem(
        id = id,
        org = org,
        repo = repo,
        number = number,
        type = ItemType.ISSUE,
        state = when (state) {
            "closed" -> ItemState.CLOSED
            else -> ItemState.OPEN
        },
        title = title,
        body = body.orEmpty(),
        labels = labels.map { it.name },
        assignees = assignees.map { it.login },
        author = user.login,
        milestone = milestone?.title,
        commentCount = comments,
        createdAt = Instant.parse(createdAt),
        updatedAt = Instant.parse(updatedAt),
        url = htmlUrl,
    )

    private fun GhPull.toOrbiItem(org: String, repo: String) = OrbiItem(
        id = id,
        org = org,
        repo = repo,
        number = number,
        type = ItemType.PR,
        state = when {
            merged -> ItemState.MERGED
            state == "closed" -> ItemState.CLOSED
            else -> ItemState.OPEN
        },
        title = title,
        body = body.orEmpty(),
        labels = labels.map { it.name },
        assignees = assignees.map { it.login },
        author = user.login,
        milestone = milestone?.title,
        commentCount = comments + reviewComments,
        createdAt = Instant.parse(createdAt),
        updatedAt = Instant.parse(updatedAt),
        url = htmlUrl,
    )

    private fun GhComment.toOrbiComment(itemId: Long) = OrbiComment(
        id = id,
        itemId = itemId,
        author = user.login,
        body = body.orEmpty(),
        createdAt = Instant.parse(createdAt),
        updatedAt = Instant.parse(updatedAt),
        canEdit = false, // Will be set by the service after comparing with authenticated user
    )

    /**
     * Map a search-result issue that has a pull_request field to an OrbiItem with type=PR.
     * The search endpoint doesn't return `merged` or `review_comments`, so we approximate.
     */
    private fun GhIssue.toOrbiPrItem(org: String, repo: String) = OrbiItem(
        id = id,
        org = org,
        repo = repo,
        number = number,
        type = ItemType.PR,
        state = when (state) {
            "closed" -> ItemState.CLOSED   // can't distinguish merged vs closed from search
            else -> ItemState.OPEN
        },
        title = title,
        body = body.orEmpty(),
        labels = labels.map { it.name },
        assignees = assignees.map { it.login },
        author = user.login,
        milestone = milestone?.title,
        commentCount = comments,
        createdAt = Instant.parse(createdAt),
        updatedAt = Instant.parse(updatedAt),
        url = htmlUrl,
    )

    private fun GhTimelineEvent.toOrbiTimelineEvent(): OrbiTimelineEvent? {
        val ts = createdAt?.let {
            try { Instant.parse(it) } catch (_: Exception) { null }
        } ?: return null

        val actorName = actor?.login
        val detail = when (event) {
            "labeled" -> "added label ${label?.name ?: "?"}"
            "unlabeled" -> "removed label ${label?.name ?: "?"}"
            "assigned" -> "assigned ${assignee?.login?.let { "@$it" } ?: "someone"}"
            "unassigned" -> "unassigned ${assignee?.login?.let { "@$it" } ?: "someone"}"
            "milestoned" -> "set milestone ${milestone?.title ?: "?"}"
            "demilestoned" -> "removed milestone ${milestone?.title ?: "?"}"
            "renamed" -> "renamed from \"${rename?.from}\" to \"${rename?.to}\""
            "closed" -> "closed"
            "reopened" -> "reopened"
            "merged" -> "merged"
            "locked" -> "locked"
            "unlocked" -> "unlocked"
            "head_ref_force_pushed" -> "force-pushed"
            "review_requested" -> "requested review"
            "review_dismissed" -> "dismissed review"
            "convert_to_draft" -> "converted to draft"
            "ready_for_review" -> "marked ready for review"
            else -> return null  // skip unknown events
        }
        return OrbiTimelineEvent(
            type = event,
            actor = actorName,
            detail = detail,
            timestamp = ts,
        )
    }
}

class GitHubApiException(val statusCode: Int, message: String) : RuntimeException(message)

