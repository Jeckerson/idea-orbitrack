package io.orbitrack.idea.services

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import io.orbitrack.idea.model.TrackedRepo

object GitRepoDetector {

    private val log = Logger.getInstance(GitRepoDetector::class.java)

    private val HTTPS_PATTERN = Regex("""url\s*=\s*https://github\.com/([^/]+)/([^/.\s]+?)(?:\.git)?\s*$""", RegexOption.MULTILINE)
    private val SSH_PATTERN = Regex("""url\s*=\s*git@github\.com:([^/]+)/([^/.\s]+?)(?:\.git)?\s*$""", RegexOption.MULTILINE)

    fun detectGitHubRepos(project: Project): List<TrackedRepo> {
        val repos = mutableSetOf<Pair<String, String>>()

        // 1. Scan project content roots (covers single-project and multi-module setups)
        ProjectRootManager.getInstance(project).contentRoots.forEach { root ->
            val gitConfig = root.findFileByRelativePath(".git/config") ?: return@forEach
            try {
                val text = String(gitConfig.contentsToByteArray(), Charsets.UTF_8)
                repos.addAll(parseGitHubRemotes(text))
            } catch (_: Exception) {
                // Skip unreadable git configs
            }
        }

        val basePath = project.basePath
        if (basePath != null) {
            val baseDir = java.io.File(basePath)

            // 2. Check project base dir itself
            collectFromDir(baseDir, repos)

            // 3. Scan immediate subdirectories of the base dir
            //    (covers: org-folder/project1, org-folder/project2, ...)
            val children = baseDir.listFiles { f -> f.isDirectory && !f.name.startsWith(".") }
            if (children != null) {
                for (child in children) {
                    collectFromDir(child, repos)
                }
            }
        }

        log.info("Detected ${repos.size} GitHub repos: $repos")
        return repos.map { (org, repo) -> TrackedRepo(org = org, repo = repo, enabled = true) }
    }

    /**
     * Reads `.git/config` in [dir] and adds any GitHub remotes found to [repos].
     */
    private fun collectFromDir(dir: java.io.File, repos: MutableSet<Pair<String, String>>) {
        try {
            val configFile = java.io.File(dir, ".git/config")
            if (configFile.exists()) {
                repos.addAll(parseGitHubRemotes(configFile.readText()))
            }
        } catch (_: Exception) {
            // Skip
        }
    }


    fun parseGitHubRemotes(gitConfig: String): List<Pair<String, String>> {
        val results = mutableListOf<Pair<String, String>>()
        for (match in HTTPS_PATTERN.findAll(gitConfig)) {
            results.add(match.groupValues[1] to match.groupValues[2])
        }
        for (match in SSH_PATTERN.findAll(gitConfig)) {
            results.add(match.groupValues[1] to match.groupValues[2])
        }
        return results.distinct()
    }
}

