package dev.anvas.orbitrack.idea.cache

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import dev.anvas.orbitrack.idea.model.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.time.Instant

/**
 * Manages reading/writing the local JSON cache for OrbiTrack items.
 * The cache is stored in `<project>/.idea/orbitrack-cache.json`.
 */
class OrbiCacheManager(private val project: Project) {

    private val log = Logger.getInstance(OrbiCacheManager::class.java)

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
        encodeDefaults = true
    }

    private val cacheFile: File?
        get() {
            val basePath = project.basePath ?: return null
            return File(basePath, ".idea/orbitrack-cache.json")
        }

    // ---- Public API ----

    fun load(): CachedState? {
        val file = cacheFile ?: return null
        if (!file.exists()) return null
        return try {
            val text = file.readText(Charsets.UTF_8)
            json.decodeFromString<CachedState>(text)
        } catch (e: Exception) {
            log.warn("Failed to read OrbiTrack cache", e)
            null
        }
    }

    fun save(state: CachedState) {
        val file = cacheFile ?: return
        try {
            file.parentFile?.mkdirs()
            file.writeText(json.encodeToString(CachedState.serializer(), state), Charsets.UTF_8)
        } catch (e: Exception) {
            log.warn("Failed to write OrbiTrack cache", e)
        }
    }

    fun delete() {
        try {
            cacheFile?.delete()
        } catch (_: Exception) { }
    }

    // ---- Conversion helpers ----

    companion object {
        fun OrbiItem.toCached() = CachedItem(
            id = id,
            org = org,
            repo = repo,
            number = number,
            type = type.name,
            state = state.name,
            title = title,
            body = body,
            labels = labels,
            assignees = assignees,
            author = author,
            milestone = milestone,
            commentCount = commentCount,
            createdAtEpoch = createdAt.toEpochMilli(),
            updatedAtEpoch = updatedAt.toEpochMilli(),
            url = url,
            headBranch = headBranch,
            baseBranch = baseBranch,
            headSha = headSha,
            isFork = isFork,
        )

        fun CachedItem.toOrbiItem() = OrbiItem(
            id = id,
            org = org,
            repo = repo,
            number = number,
            type = try { ItemType.valueOf(type) } catch (_: Exception) { ItemType.ISSUE },
            state = try { ItemState.valueOf(state) } catch (_: Exception) { ItemState.OPEN },
            title = title,
            body = body,
            labels = labels,
            assignees = assignees,
            author = author,
            milestone = milestone,
            commentCount = commentCount,
            createdAt = Instant.ofEpochMilli(createdAtEpoch),
            updatedAt = Instant.ofEpochMilli(updatedAtEpoch),
            url = url,
            headBranch = headBranch,
            baseBranch = baseBranch,
            headSha = headSha,
            isFork = isFork,
        )
    }
}

// ---- Serializable DTOs ----

@Serializable
data class CachedState(
    val items: List<CachedItem> = emptyList(),
    val trackedRepos: List<TrackedRepo> = emptyList(),
    val lastRefreshEpoch: Long? = null,
    val savedAtEpoch: Long = 0L,
    val filterState: CachedFilterState? = null,
)

@Serializable
data class CachedFilterState(
    val selectedOrg: String? = null,
    val selectedRepo: String? = null,
    val typeIndex: Int = 0,
    val stateIndex: Int = 0,
    val sortField: String = "UPDATED",
    val sortDirection: String = "DESC",
    val groupModes: List<String> = listOf("BY_TYPE"),
)

@Serializable
data class CachedItem(
    val id: Long,
    val org: String,
    val repo: String,
    val number: Int,
    val type: String,
    val state: String,
    val title: String,
    val body: String,
    val labels: List<String> = emptyList(),
    val assignees: List<String> = emptyList(),
    val author: String,
    val milestone: String? = null,
    val commentCount: Int = 0,
    val createdAtEpoch: Long,
    val updatedAtEpoch: Long,
    val url: String,
    val headBranch: String? = null,
    val baseBranch: String? = null,
    val headSha: String? = null,
    val isFork: Boolean = false,
)

