package io.orbitrack.idea.services

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import io.orbitrack.idea.api.GhPullDetail
import io.orbitrack.idea.model.OrbiComment
import io.orbitrack.idea.model.OrbiItem
import io.orbitrack.idea.model.OrbiTimelineEvent
import io.orbitrack.idea.model.ItemType
import io.orbitrack.idea.model.ItemState
import io.orbitrack.idea.model.TrackedRepo
import kotlinx.coroutines.*
import java.time.Instant

@Service(Service.Level.PROJECT)
class OrbiTrackProjectService(private val project: Project) : Disposable {

    companion object {
        fun getInstance(project: Project): OrbiTrackProjectService =
            project.getService(OrbiTrackProjectService::class.java)
    }

    private val log = Logger.getInstance(OrbiTrackProjectService::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    var trackedRepos: List<TrackedRepo> = emptyList()
        private set

    var items: List<OrbiItem> = emptyList()
        private set

    private val commentsMap = mutableMapOf<Int, List<OrbiComment>>()
    private val timelineMap = mutableMapOf<Int, List<OrbiTimelineEvent>>()
    private val pullDetailMap = mutableMapOf<Int, GhPullDetail>()
    private var authenticatedUser: String? = null

    /** Timestamp of the last successful refresh, used for incremental fetches via Search API. */
    private var lastRefreshTimestamp: Instant? = null

    // Listeners
    fun interface DataListener {
        fun onDataChanged()
    }

    private val listeners = mutableListOf<DataListener>()

    fun addDataListener(listener: DataListener) {
        listeners.add(listener)
    }

    fun removeDataListener(listener: DataListener) {
        listeners.remove(listener)
    }

    private fun notifyListeners() {
        listeners.forEach {
            try {
                it.onDataChanged()
            } catch (e: Exception) {
                log.warn("Listener error", e)
            }
        }
    }

    var isLoading: Boolean = false
        private set

    var lastError: String? = null
        private set

    private var currentPage = 1
    var hasMore: Boolean = true
        private set

    // ---- Auto-detection ----

    fun detectRepos() {
        trackedRepos = GitRepoDetector.detectGitHubRepos(project)
        log.info("Detected ${trackedRepos.size} GitHub repos: ${trackedRepos.map { "${it.org}/${it.repo}" }}")
    }

    // ---- Data fetching ----

    /**
     * Re-fetches a single issue or PR from GitHub, updating the cached item,
     * its comments, timeline, and (for PRs) merge/branch details.
     */
    fun refreshItem(item: OrbiItem, onResult: ((Boolean, String?) -> Unit)? = null) {
        scope.launch {
            try {
                val client = OrbiTrackAppService.getInstance().getClient()
                    ?: run { onResult?.invoke(false, "No GitHub token configured."); return@launch }

                // Fetch fresh item data
                val freshItem = if (item.type == ItemType.PR) {
                    client.getPull(item.org, item.repo, item.number)
                } else {
                    client.getIssue(item.org, item.repo, item.number)
                }

                // Update in the items cache
                val key = Triple(item.org, item.repo, item.number)
                val existingByKey = items.associateBy { Triple(it.org, it.repo, it.number) }.toMutableMap()
                existingByKey[key] = freshItem
                items = existingByKey.values.sortedByDescending { it.updatedAt }

                // Invalidate and reload comments
                commentsMap.remove(item.number)
                val comments = client.getComments(item.org, item.repo, item.number, perPage = 30, page = 1)
                    .map { c -> c.copy(itemId = item.id, canEdit = (authenticatedUser != null && c.author == authenticatedUser)) }
                commentsMap[item.number] = comments

                // Invalidate and reload timeline
                timelineMap.remove(item.number)
                val events = client.getTimeline(item.org, item.repo, item.number)
                timelineMap[item.number] = events

                // For PRs, reload merge/branch detail
                if (freshItem.type == ItemType.PR) {
                    pullDetailMap.remove(item.number)
                    // loadPullDetail will enrich the item and notify again
                    loadPullDetail(freshItem, forceReload = true)
                }

                onResult?.invoke(true, null)
                notifyListeners()
            } catch (e: Exception) {
                log.warn("Failed to refresh item ${item.org}/${item.repo}#${item.number}", e)
                onResult?.invoke(false, e.message)
            }
        }
    }

    /**
     * @param forceFullRefresh when true, bypass the incremental cache and re-fetch
     *        everything from scratch (used by the manual Refresh button).
     */
    fun refresh(forceFullRefresh: Boolean = false) {
        if (isLoading) return
        if (forceFullRefresh) {
            lastRefreshTimestamp = null
        }
        scope.launch {
            isLoading = true
            lastError = null
            notifyListeners()

            try {
                val client = OrbiTrackAppService.getInstance().getClient()
                if (client == null) {
                    lastError = "No GitHub token configured. Go to Settings \u2192 Tools \u2192 OrbiTrack."
                    isLoading = false
                    notifyListeners()
                    return@launch
                }

                // Get authenticated user for canEdit checks
                if (authenticatedUser == null) {
                    authenticatedUser = client.getAuthenticatedUser()
                }

                if (trackedRepos.isEmpty()) {
                    detectRepos()
                }

                if (trackedRepos.isEmpty()) {
                    lastError = "No GitHub repositories detected in this project."
                    isLoading = false
                    notifyListeners()
                    return@launch
                }

                val enabledRepos = trackedRepos.filter { it.enabled }
                val refreshStart = Instant.now()

                if (lastRefreshTimestamp != null && items.isNotEmpty()) {
                    // ---- Incremental refresh via batched Search API ----
                    performIncrementalRefresh(client, enabledRepos, refreshStart)
                } else {
                    // ---- Full initial fetch ----
                    performFullRefresh(client, enabledRepos, refreshStart)
                }
            } catch (e: Exception) {
                log.error("Refresh failed", e)
                lastError = "Fetch failed: ${e.message}"
                isLoading = false
                notifyListeners()
            }
        }
    }

    private suspend fun performFullRefresh(
        client: io.orbitrack.idea.api.GitHubClient,
        enabledRepos: List<TrackedRepo>,
        refreshStart: Instant
    ) {
        val freshItems = mutableListOf<OrbiItem>()
        for (repo in enabledRepos) {
            try {
                val issues = client.listIssues(repo.org, repo.repo, state = "open", perPage = 20, page = 1)
                val prs = client.listPRs(repo.org, repo.repo, state = "open", perPage = 20, page = 1)
                freshItems.addAll(issues)
                freshItems.addAll(prs)
            } catch (e: Exception) {
                log.warn("Failed to fetch ${repo.org}/${repo.repo}", e)
            }
        }

        // Delta merge: update existing, add new, keep items not in this fetch
        val freshByKey = freshItems.associateBy { Triple(it.org, it.repo, it.number) }
        val existingByKey = items.associateBy { Triple(it.org, it.repo, it.number) }.toMutableMap()

        for ((key, freshItem) in freshByKey) {
            val old = existingByKey[key]
            if (old == null || old.updatedAt != freshItem.updatedAt) {
                existingByKey[key] = freshItem
                if (old != null && old.updatedAt != freshItem.updatedAt) {
                    commentsMap.remove(freshItem.number)
                    timelineMap.remove(freshItem.number)
                }
            }
        }

        // Remove items that were open but are no longer returned
        val trackedOrgRepos = enabledRepos.map { it.org to it.repo }.toSet()
        existingByKey.entries.removeAll { (key, _) ->
            val (org, repo, _) = key
            (org to repo) in trackedOrgRepos && key !in freshByKey
        }

        items = existingByKey.values.sortedByDescending { it.updatedAt }
        currentPage = 1
        hasMore = freshItems.size >= 20
        lastRefreshTimestamp = refreshStart
        isLoading = false
        notifyListeners()
    }

    private suspend fun performIncrementalRefresh(
        client: io.orbitrack.idea.api.GitHubClient,
        enabledRepos: List<TrackedRepo>,
        refreshStart: Instant
    ) {
        try {
            val repoPairs = enabledRepos.map { it.org to it.repo }
            val updatedItems = mutableListOf<OrbiItem>()
            var page = 1
            // Paginate search results (up to 5 pages of 100 to stay within rate limits)
            do {
                val batch = client.searchUpdatedItems(repoPairs, lastRefreshTimestamp!!, perPage = 100, page = page)
                updatedItems.addAll(batch)
                page++
            } while (batch.size == 100 && page <= 5)

            // Merge updated items into existing cache
            val existingByKey = items.associateBy { Triple(it.org, it.repo, it.number) }.toMutableMap()

            for (freshItem in updatedItems) {
                val key = Triple(freshItem.org, freshItem.repo, freshItem.number)
                val old = existingByKey[key]

                // Item's state changed (e.g. open → closed/merged) — remove from cache
                if (old != null && old.state != freshItem.state) {
                    existingByKey.remove(key)
                    commentsMap.remove(freshItem.number)
                    timelineMap.remove(freshItem.number)
                    continue
                }

                if (old == null || old.updatedAt != freshItem.updatedAt) {
                    existingByKey[key] = freshItem
                    commentsMap.remove(freshItem.number)
                    timelineMap.remove(freshItem.number)
                }
            }

            items = existingByKey.values.sortedByDescending { it.updatedAt }
            lastRefreshTimestamp = refreshStart
            isLoading = false
            notifyListeners()
        } catch (e: Exception) {
            log.warn("Incremental refresh failed, falling back to full refresh", e)
            // Reset timestamp so next call does a full refresh
            lastRefreshTimestamp = null
            performFullRefresh(client, enabledRepos, refreshStart)
        }
    }

    fun fetchMore() {
        if (isLoading || !hasMore) return
        scope.launch {
            isLoading = true
            notifyListeners()
            try {
                val client = OrbiTrackAppService.getInstance().getClient() ?: return@launch
                val nextPage = currentPage + 1
                val moreItems = mutableListOf<OrbiItem>()
                for (repo in trackedRepos.filter { it.enabled }) {
                    try {
                        val issues = client.listIssues(repo.org, repo.repo, state = "open", perPage = 20, page = nextPage)
                        val prs = client.listPRs(repo.org, repo.repo, state = "open", perPage = 20, page = nextPage)
                        moreItems.addAll(issues)
                        moreItems.addAll(prs)
                    } catch (e: Exception) {
                        log.warn("Failed to fetch more from ${repo.org}/${repo.repo}", e)
                    }
                }
                if (moreItems.isEmpty()) {
                    hasMore = false
                } else {
                    currentPage = nextPage
                    hasMore = moreItems.size >= 20
                    val existingKeys = items.map { Triple(it.org, it.repo, it.number) }.toSet()
                    val newOnly = moreItems.filter { Triple(it.org, it.repo, it.number) !in existingKeys }
                    items = (items + newOnly).sortedByDescending { it.updatedAt }
                }
                isLoading = false
                notifyListeners()
            } catch (e: Exception) {
                log.warn("Fetch more failed", e)
                isLoading = false
                notifyListeners()
            }
        }
    }

    // ---- Create Issue ----

    fun createIssue(
        org: String,
        repo: String,
        title: String,
        body: String,
        labels: List<String>,
        assignees: List<String>,
        onResult: (Boolean, String?) -> Unit
    ) {
        scope.launch {
            try {
                val client = OrbiTrackAppService.getInstance().getClient()
                    ?: run { onResult(false, "No GitHub token configured."); return@launch }
                val newItem = client.createIssue(org, repo, title, body, labels, assignees)
                // Prepend to cached items
                items = listOf(newItem) + items
                onResult(true, null)
                notifyListeners()
            } catch (e: Exception) {
                log.warn("Failed to create issue in $org/$repo", e)
                onResult(false, e.message)
            }
        }
    }

    // ---- Comments (lazy, on-demand) ----

    fun getComments(itemNumber: Int): List<OrbiComment> = commentsMap[itemNumber].orEmpty()

    fun hasCommentsCached(itemNumber: Int): Boolean = commentsMap.containsKey(itemNumber)

    fun loadComments(item: OrbiItem) {
        if (commentsMap.containsKey(item.number)) return
        scope.launch {
            try {
                val client = OrbiTrackAppService.getInstance().getClient() ?: return@launch
                val comments = client.getComments(item.org, item.repo, item.number, perPage = 30, page = 1)
                    .map { c -> c.copy(itemId = item.id, canEdit = (authenticatedUser != null && c.author == authenticatedUser)) }
                commentsMap[item.number] = comments
                notifyListeners()
            } catch (e: Exception) {
                log.warn("Failed to fetch comments for ${item.org}/${item.repo}#${item.number}", e)
            }
        }
    }

    fun postComment(item: OrbiItem, body: String, onResult: (Boolean, String?) -> Unit) {
        scope.launch {
            try {
                val client = OrbiTrackAppService.getInstance().getClient()
                    ?: run { onResult(false, "No GitHub token configured."); return@launch }
                client.createComment(item.org, item.repo, item.number, body)
                refreshComments(item)
                onResult(true, null)
                notifyListeners()
            } catch (e: Exception) {
                log.warn("Failed to post comment on ${item.org}/${item.repo}#${item.number}", e)
                onResult(false, e.message)
            }
        }
    }

    fun editComment(item: OrbiItem, commentId: Long, body: String, onResult: (Boolean, String?) -> Unit) {
        scope.launch {
            try {
                val client = OrbiTrackAppService.getInstance().getClient()
                    ?: run { onResult(false, "No GitHub token configured."); return@launch }
                client.updateComment(item.org, item.repo, commentId, body)
                refreshComments(item)
                onResult(true, null)
                notifyListeners()
            } catch (e: Exception) {
                log.warn("Failed to edit comment $commentId on ${item.org}/${item.repo}#${item.number}", e)
                onResult(false, e.message)
            }
        }
    }

    fun deleteComment(item: OrbiItem, commentId: Long, onResult: (Boolean, String?) -> Unit) {
        scope.launch {
            try {
                val client = OrbiTrackAppService.getInstance().getClient()
                    ?: run { onResult(false, "No GitHub token configured."); return@launch }
                client.deleteComment(item.org, item.repo, commentId)
                refreshComments(item)
                onResult(true, null)
                notifyListeners()
            } catch (e: Exception) {
                log.warn("Failed to delete comment $commentId on ${item.org}/${item.repo}#${item.number}", e)
                onResult(false, e.message)
            }
        }
    }

    private suspend fun refreshComments(item: OrbiItem) {
        val client = OrbiTrackAppService.getInstance().getClient() ?: return
        commentsMap.remove(item.number)
        val fresh = client.getComments(item.org, item.repo, item.number, perPage = 30, page = 1)
            .map { c -> c.copy(itemId = item.id, canEdit = (authenticatedUser != null && c.author == authenticatedUser)) }
        commentsMap[item.number] = fresh
    }

    // ---- Timeline (lazy, on-demand) ----

    fun getTimeline(itemNumber: Int): List<OrbiTimelineEvent> = timelineMap[itemNumber].orEmpty()

    fun loadTimeline(item: OrbiItem) {
        if (timelineMap.containsKey(item.number)) return
        scope.launch {
            try {
                val client = OrbiTrackAppService.getInstance().getClient() ?: return@launch
                val events = client.getTimeline(item.org, item.repo, item.number)
                timelineMap[item.number] = events
                notifyListeners()
            } catch (e: Exception) {
                log.warn("Failed to fetch timeline for ${item.org}/${item.repo}#${item.number}", e)
            }
        }
    }

    // ---- PR Detail (mergeability + branch refs) ----

    fun getPullDetail(itemNumber: Int): GhPullDetail? = pullDetailMap[itemNumber]

    fun loadPullDetail(item: OrbiItem, forceReload: Boolean = false) {
        if (item.type != ItemType.PR) return
        if (!forceReload && pullDetailMap.containsKey(item.number)) return
        scope.launch {
            try {
                val client = OrbiTrackAppService.getInstance().getClient() ?: return@launch
                val detail = client.getPullDetail(item.org, item.repo, item.number)
                pullDetailMap[item.number] = detail

                // Enrich the cached OrbiItem with branch/merge info
                val key = Triple(item.org, item.repo, item.number)
                val idx = items.indexOfFirst { Triple(it.org, it.repo, it.number) == key }
                if (idx >= 0) {
                    val isFork = detail.head.repo?.fork == true
                            || detail.head.repo?.fullName != detail.base.repo?.fullName
                    val updated = items[idx].copy(
                        headBranch = detail.head.ref,
                        baseBranch = detail.base.ref,
                        headSha = detail.head.sha,
                        isFork = isFork,
                        mergeable = detail.mergeable,
                        mergeableState = detail.mergeableState,
                    )
                    items = items.toMutableList().also { it[idx] = updated }
                }

                // If GitHub hasn't computed mergeability yet (null), retry once after a short delay
                if (detail.mergeable == null) {
                    kotlinx.coroutines.delay(2000)
                    try {
                        val retried = client.getPullDetail(item.org, item.repo, item.number)
                        pullDetailMap[item.number] = retried
                        val idx2 = items.indexOfFirst { Triple(it.org, it.repo, it.number) == key }
                        if (idx2 >= 0) {
                            val isFork2 = retried.head.repo?.fork == true
                                    || retried.head.repo?.fullName != retried.base.repo?.fullName
                            items = items.toMutableList().also {
                                it[idx2] = it[idx2].copy(
                                    mergeable = retried.mergeable,
                                    mergeableState = retried.mergeableState,
                                    isFork = isFork2,
                                )
                            }
                        }
                    } catch (_: Exception) { /* best-effort retry */ }
                }

                notifyListeners()
            } catch (e: Exception) {
                log.warn("Failed to fetch PR detail for ${item.org}/${item.repo}#${item.number}", e)
            }
        }
    }

    // ---- Merge PR ----

    fun mergePull(
        item: OrbiItem,
        mergeMethod: String,
        onResult: (Boolean, String?) -> Unit,
    ) {
        scope.launch {
            try {
                val client = OrbiTrackAppService.getInstance().getClient()
                    ?: run { onResult(false, "No GitHub token configured."); return@launch }
                val result = client.mergePull(
                    org = item.org,
                    repo = item.repo,
                    number = item.number,
                    mergeMethod = mergeMethod,
                    sha = item.headSha,
                )
                if (result.merged) {
                    // Update cached item state
                    val key = Triple(item.org, item.repo, item.number)
                    val idx = items.indexOfFirst { Triple(it.org, it.repo, it.number) == key }
                    if (idx >= 0) {
                        items = items.toMutableList().also {
                            it[idx] = it[idx].copy(state = ItemState.MERGED)
                        }
                    }
                    pullDetailMap.remove(item.number)
                    onResult(true, null)
                    notifyListeners()
                } else {
                    onResult(false, result.message)
                }
            } catch (e: Exception) {
                log.warn("Failed to merge PR ${item.org}/${item.repo}#${item.number}", e)
                onResult(false, e.message)
            }
        }
    }

    // ---- Checkout branch (via git CLI) ----

    fun checkoutBranch(item: OrbiItem, onResult: (Boolean, String?) -> Unit) {
        if (item.headBranch.isNullOrBlank()) {
            onResult(false, "Branch name not available. Load PR details first.")
            return
        }
        scope.launch {
            try {
                val basePath = project.basePath
                    ?: run { onResult(false, "Cannot determine project base path."); return@launch }

                // Find the correct working directory — may be a subdirectory matching the repo
                val workDir = findRepoDir(basePath, item.org, item.repo)
                    ?: run { onResult(false, "Cannot find local git repo for ${item.org}/${item.repo}."); return@launch }

                val branch = item.headBranch!!
                val isFork = item.isFork

                if (isFork) {
                    // For fork PRs: fetch the head ref from GitHub's special PR refs
                    val fetchResult = runGitCommand(workDir, "git", "fetch", "origin", "pull/${item.number}/head:$branch")
                    if (fetchResult.exitCode != 0) {
                        onResult(false, "git fetch failed: ${fetchResult.stderr}")
                        return@launch
                    }
                } else {
                    // For same-repo PRs: fetch latest and checkout the branch
                    val fetchResult = runGitCommand(workDir, "git", "fetch", "origin")
                    if (fetchResult.exitCode != 0) {
                        onResult(false, "git fetch failed: ${fetchResult.stderr}")
                        return@launch
                    }
                }

                val checkoutResult = runGitCommand(workDir, "git", "checkout", branch)
                if (checkoutResult.exitCode != 0) {
                    // Branch may not exist locally yet, try creating from remote
                    val trackResult = runGitCommand(workDir, "git", "checkout", "-b", branch, "origin/$branch")
                    if (trackResult.exitCode != 0 && !isFork) {
                        onResult(false, "git checkout failed: ${trackResult.stderr}")
                        return@launch
                    } else if (trackResult.exitCode != 0) {
                        // For fork, we already fetched into the branch name above
                        val retryResult = runGitCommand(workDir, "git", "checkout", branch)
                        if (retryResult.exitCode != 0) {
                            onResult(false, "git checkout failed: ${retryResult.stderr}")
                            return@launch
                        }
                    }
                }

                // Refresh VFS so the IDE picks up file changes
                withContext(Dispatchers.Main) {
                    com.intellij.openapi.vfs.VirtualFileManager.getInstance().asyncRefresh {}
                }

                onResult(true, null)
            } catch (e: Exception) {
                log.warn("Failed to checkout branch for ${item.org}/${item.repo}#${item.number}", e)
                onResult(false, e.message)
            }
        }
    }

    private fun findRepoDir(basePath: String, org: String, repo: String): java.io.File? {
        val baseDir = java.io.File(basePath)

        // Check if the base dir itself is the repo
        if (isGitRepoMatching(baseDir, org, repo)) return baseDir

        // Check immediate subdirectories
        val children = baseDir.listFiles { f -> f.isDirectory && !f.name.startsWith(".") }
        if (children != null) {
            for (child in children) {
                if (isGitRepoMatching(child, org, repo)) return child
            }
        }

        // Fallback: just use basePath if it has a .git dir
        if (java.io.File(baseDir, ".git").exists()) return baseDir

        return null
    }

    private fun isGitRepoMatching(dir: java.io.File, org: String, repo: String): Boolean {
        val configFile = java.io.File(dir, ".git/config")
        if (!configFile.exists()) return false
        return try {
            val remotes = GitRepoDetector.parseGitHubRemotes(configFile.readText())
            remotes.any { it.first.equals(org, ignoreCase = true) && it.second.equals(repo, ignoreCase = true) }
        } catch (_: Exception) {
            false
        }
    }

    private data class GitResult(val exitCode: Int, val stdout: String, val stderr: String)

    private suspend fun runGitCommand(workDir: java.io.File, vararg command: String): GitResult =
        withContext(Dispatchers.IO) {
            try {
                val process = ProcessBuilder(*command)
                    .directory(workDir)
                    .redirectErrorStream(false)
                    .start()
                val stdout = process.inputStream.bufferedReader().readText()
                val stderr = process.errorStream.bufferedReader().readText()
                val exitCode = process.waitFor()
                GitResult(exitCode, stdout.trim(), stderr.trim())
            } catch (e: Exception) {
                GitResult(-1, "", e.message ?: "Unknown error")
            }
        }

    override fun dispose() {
        scope.cancel()
        listeners.clear()
    }
}
