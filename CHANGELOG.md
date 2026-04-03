# Changelog

All notable changes to the **OrbiTrack** IntelliJ plugin will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.3] — 2026-04-03

### Added

- **"Create .md file" button** in the issue/PR detail action bar — generates a structured Markdown file (`{type}-{org}-{repo}-{number}.md`) in the project root containing the full title, metadata (repo, state, labels, author, assignees, milestone, URL, dates), body, and all currently cached comments; silently overwrites any existing file and immediately opens it in the IDE editor

## [1.0.2] — 2026-03-26

### Fixed

- Extended IDE compatibility range from `253.*` to `261.*` to support IntelliJ Platform 2026.1 builds

## [1.0.1] — 2026-03-22

### Fixed

- Extended IDE compatibility range from `251.*` to `253.*` to support IntelliJ Platform 2025.3 builds
- Replaced deprecated `SlowOperations.allowSlowOperations()` call in `OrbiTrackConfigurable.reset()` with proper background-thread execution (`executeOnPooledThread`) to avoid future removal breakage and comply with IntelliJ Platform threading guidelines

## [1.0.0] — 2026-03-21

### Added

#### Plugin Scaffold
- Gradle build (`build.gradle.kts`) with IntelliJ Platform Plugin SDK v2 (`org.jetbrains.intellij.platform 2.13.1`)
- Kotlin 2.1.10, JVM toolchain 21
- `plugin.xml` registration for tool window, application/project services, settings page, and actions
- Apache 2.0 license

#### Data Models
- `OrbiItem` — unified issue/PR model with PR-specific fields (head/base branch, mergeability, fork detection)
- `OrbiComment` — comment model with `canEdit` flag for the authenticated user
- `OrbiTimelineEvent` — timeline event model (labeled, assigned, closed, merged, renamed, etc.)
- `TrackedRepo` — tracked repository configuration (org, repo, enabled)
- `ItemType` (ISSUE, PR) and `ItemState` (OPEN, CLOSED, MERGED) enums

#### GitHub API Client
- Full `GitHubClient` interface with OkHttp-based implementation (`OkHttpGitHubClient`)
- Issue and PR listing with pagination, sorting, and state filtering
- Comment CRUD — create, read, update, delete comments inline
- Issue creation with labels and assignees
- Single issue/PR fetch by number
- Authenticated user detection for `canEdit` checks
- Organization and repository listing
- Timeline event retrieval
- PR detail fetch (mergeability, branch refs, fork detection)
- PR merge support (merge, squash, rebase strategies)
- Batched incremental refresh via GitHub Search API with automatic chunking (5 repos per batch) and fallback on 422 errors
- Rate limit awareness and configurable timeouts (15s connect, 30s read)
- Serializable GitHub API models (`GhIssue`, `GhPull`, `GhComment`, `GhPullDetail`, `GhMergeResult`, etc.)

#### Local Cache
- JSON-based cache persistence (`OrbiCacheManager`) stored in `.idea/orbitrack-cache.json`
- Cache-first startup — previously loaded items display instantly on IDE restart
- Filter state persistence across sessions
- Tracked repo list persistence
- Last refresh timestamp tracking for incremental sync
- Automatic cache save on data changes and project close

#### Services
- `OrbiTrackAppService` — application-level service for GitHub PAT storage via IDE credential store (`PasswordSafe`)
- `OrbiTrackProjectService` — project-level service managing data lifecycle:
  - Automatic GitHub repo detection from `.git/config` on project open
  - Full refresh and incremental refresh (delta merge via Search API)
  - Paginated "load more" support
  - Lazy on-demand loading of comments, timeline, and PR details
  - PR merge execution with optimistic state update
  - PR branch checkout via `git` CLI (supports same-repo and fork PRs)
  - Issue creation with immediate cache update
  - Listener-based UI notification pattern
- `GitRepoDetector` — scans project content roots, base directory, and immediate subdirectories for GitHub remotes (HTTPS and SSH URL patterns)

#### Tool Window UI
- `OrbiTrackToolWindowFactory` — registers the OrbiTrack tool window (right anchor, custom icon)
- `OrbiTrackPanel` — main split-pane layout:
  - Left panel: filter controls + scrollable item list + "Load more" button
  - Right panel: item detail view
  - Toolbar with Refresh and New Issue actions
- `FilterPanel` — multi-criteria filtering:
  - Filter by organization, repository, type (Issues/PRs), and state (Open/Closed/Merged)
  - Sort by last updated, created date, or ID (ascending/descending)
  - Multi-select grouping (by org, by repo, by type) with nested hierarchy
  - Filter and sort state restoration from cache
- `ItemCellRenderer` — color-coded list cells with state indicators, labels, author, relative timestamps
- `ListEntry` — sealed class supporting grouped headers and item entries with nesting depth
- `ItemDetailPanel` — rich detail view:
  - Linked title and author (opens GitHub in browser)
  - State badge with color coding (green/red/purple for open/closed/merged)
  - Label badges, assignees, milestone display
  - Markdown-rendered issue/PR body
  - Comment thread with author links and timestamps
  - Inline comment editing and deletion (for authenticated user's own comments)
  - Timeline / history section with emoji icons per event type
  - "Open in Browser", "Refresh", "Add Comment", "Copy LLM Context" action buttons
  - PR-specific: mergeability status indicator, branch info, merge button (with method chooser), checkout button
  - Loading overlay for per-item refresh
  - Scroll position preservation on refresh
- `MarkdownRenderer` — lightweight markdown-to-HTML converter:
  - Headings, bold, italic, inline code, fenced code blocks
  - Links, images, unordered/ordered lists, blockquotes, horizontal rules
  - HTML tag stripping (for GitHub bot comments)
  - IDE-native font and color theming (light/dark mode support)

#### Actions
- `RefreshAction` — manual full refresh from toolbar (disabled while loading)
- `CreateIssueAction` — dialog for creating new GitHub issues with repo selection, title, body, labels, and assignees

#### Settings
- `OrbiTrackConfigurable` — settings page under Tools → OrbiTrack
- GitHub Personal Access Token input (stored securely in IDE credential store)

#### LLM Context Extraction
- "Copy LLM Context" button produces structured markdown to clipboard:
  - Issue/PR title, repo, state, labels, author, assignees, milestone
  - Full description body
  - Complete comment thread with authors and timestamps
  - Metadata (created/updated dates, GitHub URL)

#### Testing
- Basic smoke test (`SmokeTest.kt`)
