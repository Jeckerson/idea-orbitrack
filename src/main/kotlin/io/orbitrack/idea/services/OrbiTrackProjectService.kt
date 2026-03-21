package io.orbitrack.idea.services

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import io.orbitrack.idea.model.OrbiComment
import io.orbitrack.idea.model.OrbiItem
import io.orbitrack.idea.model.TrackedRepo
import kotlinx.coroutines.*

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
    private var authenticatedUser: String? = null

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

    fun refresh() {
        if (isLoading) return
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

                // Fetch only latest 20 open issues + 20 open PRs per repo (single page)
                val freshItems = mutableListOf<OrbiItem>()
                for (repo in trackedRepos.filter { it.enabled }) {
                    try {
                        val issues = client.listIssues(repo.org, repo.repo, state = "open", perPage = 20, page = 1)
                        val prs = client.listPRs(repo.org, repo.repo, state = "open", perPage = 20, page = 1)
                        freshItems.addAll(issues)
                        freshItems.addAll(prs)
                    } catch (e: Exception) {
                        log.warn("Failed to fetch ${repo.org}/${repo.repo}", e)
                    }
                }

                // Delta merge: update existing, add new, keep items not in this fetch (other repos/states)
                val freshByKey = freshItems.associateBy { Triple(it.org, it.repo, it.number) }
                val existingByKey = items.associateBy { Triple(it.org, it.repo, it.number) }.toMutableMap()

                // Update or add
                for ((key, freshItem) in freshByKey) {
                    val old = existingByKey[key]
                    if (old == null || old.updatedAt != freshItem.updatedAt) {
                        existingByKey[key] = freshItem
                        // Invalidate comment cache for updated items
                        if (old != null && old.updatedAt != freshItem.updatedAt) {
                            commentsMap.remove(freshItem.number)
                        }
                    }
                }

                // Remove items that were open but are no longer returned (closed/merged since last fetch)
                val trackedOrgRepos = trackedRepos.filter { it.enabled }.map { it.org to it.repo }.toSet()
                existingByKey.entries.removeAll { (key, _) ->
                    val (org, repo, _) = key
                    (org to repo) in trackedOrgRepos && key !in freshByKey
                }

                items = existingByKey.values.sortedByDescending { it.updatedAt }
                currentPage = 1
                hasMore = freshItems.size >= 20 // If we got a full page, there might be more
                isLoading = false
                notifyListeners()
            } catch (e: Exception) {
                log.error("Refresh failed", e)
                lastError = "Fetch failed: ${e.message}"
                isLoading = false
                notifyListeners()
            }
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

    override fun dispose() {
        scope.cancel()
        listeners.clear()
    }
}
