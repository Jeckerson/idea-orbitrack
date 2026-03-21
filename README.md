# OrbiTrack

**JetBrains plugin to aggregate GitHub issues and PRs across organizations and repositories into a single IDE-native interface with LLM context extraction.**

[![License: Apache 2.0](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)
[![JetBrains Plugin](https://img.shields.io/badge/JetBrains-Plugin-orange.svg)](https://plugins.jetbrains.com/)
[![IntelliJ IDEA](https://img.shields.io/badge/IntelliJ_IDEA-2025.1+-purple.svg)](https://www.jetbrains.com/idea/)

---

## Overview

OrbiTrack brings your GitHub issues and pull requests into your JetBrains IDE. Instead of switching between browser tabs, you can browse, filter, comment on, and manage issues and PRs from multiple repositories — all from a single tool window.

### Key Features

- 🔍 **Unified View** — See issues and PRs from all your tracked repositories in one list
- 🔄 **Incremental Sync** — Fast delta updates via the GitHub Search API; full data on first load
- 💬 **Inline Comments** — Read, create, edit, and delete comments without leaving the IDE
- 🏷️ **Rich Filtering** — Filter by org, repo, type (issue/PR), and state (open/closed/merged)
- 📊 **Grouping & Sorting** — Group by org, repo, or type; sort by updated date, created date, or ID
- 🔀 **PR Management** — View mergeability status, merge PRs (merge/squash/rebase), and checkout branches
- 🤖 **LLM Context** — One-click copy of structured issue context to clipboard for use with AI assistants
- 📝 **Markdown Rendering** — Issue bodies and comments rendered as formatted HTML
- ⏱️ **Timeline** — View issue/PR history (labels, assignments, closures, renames, merges)
- 🆕 **Create Issues** — Open new GitHub issues directly from the IDE
- 🗂️ **Cache-first** — Previously loaded data appears instantly on IDE restart
- 🔐 **Secure Auth** — GitHub PAT stored in the IDE's native credential store

---

## Screenshots

<!-- Add screenshots here -->
<!-- ![Tool Window](docs/screenshots/tool-window.png) -->

---

## Installation

### From JetBrains Marketplace

1. Open your JetBrains IDE (IntelliJ IDEA, WebStorm, PyCharm, etc.)
2. Go to **Settings** → **Plugins** → **Marketplace**
3. Search for **OrbiTrack**
4. Click **Install** and restart the IDE

### From Disk

1. Download the latest `.zip` from [Releases](https://github.com/Jeckerson/idea-orbitrack/releases)
2. Go to **Settings** → **Plugins** → ⚙️ → **Install Plugin from Disk…**
3. Select the downloaded `.zip` file
4. Restart the IDE

---

## Setup

### 1. Generate a GitHub Personal Access Token

1. Go to [GitHub → Settings → Developer settings → Personal access tokens → Tokens (classic)](https://github.com/settings/tokens)
2. Click **Generate new token (classic)**
3. Select scopes:
   - `repo` — Full control of private repositories (or `public_repo` for public only)
   - `read:org` — Read organization membership
4. Copy the generated token

### 2. Configure OrbiTrack

1. Go to **Settings** → **Tools** → **OrbiTrack**
2. Paste your GitHub Personal Access Token
3. Click **Apply**

### 3. Open the Tool Window

1. Open a project that contains GitHub repositories
2. Click the **OrbiTrack** tab on the right side of the IDE (or via **View** → **Tool Windows** → **OrbiTrack**)
3. OrbiTrack will automatically detect GitHub repos from your project's `.git/config` and start syncing

---

## Usage

### Browsing Issues & PRs

The tool window is split into two panels:

- **Left panel** — Filterable list of issues and PRs across all tracked repos
- **Right panel** — Detail view for the selected item

Use the filter dropdowns to narrow by:
| Filter | Options |
|--------|---------|
| **Org** | All Orgs, or a specific organization |
| **Repo** | All Repos, or a specific repository |
| **Type** | All Types, Issues, PRs |
| **State** | Open, All States, Closed, Merged |

### Sorting & Grouping

- **Sort** by Last Updated, Created Date, or ID (ascending/descending)
- **Group** by any combination of Org, Repo, and Type for hierarchical views

### Comments

- Click **+ Add Comment** to post a new comment
- Your own comments show **Edit** and **Delete** buttons
- Comments are rendered with full markdown support

### Pull Request Actions

For open PRs, OrbiTrack shows:
- ✅ **Mergeability status** (clean, conflicts, or checking)
- **Branch info** (head → base)
- **Merge PR** button with method chooser (merge commit, squash, rebase)
- **Checkout Branch** button to switch to the PR branch locally (supports fork PRs)

### LLM Context Extraction

Click **Copy LLM Context** to copy a structured summary of the issue/PR to your clipboard, including:
- Title, repo, state, labels, author, assignees
- Full description
- All comments with authors and timestamps
- Metadata and GitHub URL

This is designed to be pasted into AI assistant conversations for context-aware help.

### Creating Issues

Click the **+** button in the toolbar to create a new GitHub issue:
- Select the target repository
- Enter title, body (markdown), labels, and assignees
- The issue is created via the GitHub API and immediately appears in your list

---

## How It Works

### Auto-Detection

On project open, OrbiTrack scans for GitHub repositories by:
1. Reading `.git/config` from project content roots
2. Checking the project base directory
3. Scanning immediate subdirectories (supports monorepo/org-folder layouts)

Both HTTPS (`https://github.com/org/repo.git`) and SSH (`git@github.com:org/repo.git`) remotes are recognized.

### Sync Strategy

- **First load** — Fetches issues and PRs from each tracked repo via the GitHub REST API
- **Subsequent refreshes** — Uses the GitHub Search API with `updated:>timestamp` for fast incremental updates
- **Delta merge** — New and updated items are merged into the cache; stale items are pruned by state
- **Manual refresh** — The toolbar Refresh button forces a full re-fetch

### Caching

All data is cached in `.idea/orbitrack-cache.json`:
- Items, tracked repos, filter state, and last refresh timestamp are persisted
- On IDE restart, cached data is displayed immediately while a background sync runs
- Cache is updated automatically on every data change and at project close

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Kotlin 2.1.10 |
| Build | Gradle (Kotlin DSL) + [IntelliJ Platform Plugin SDK v2](https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html) |
| Platform | IntelliJ IDEA 2025.1+ (Community & Ultimate) |
| GitHub API | OkHttp 4.12 + `kotlinx.serialization` 1.6.3 |
| Local Cache | JSON file (`.idea/orbitrack-cache.json`) |
| Async | Kotlin Coroutines (`kotlinx.coroutines`) |
| UI | JetBrains Platform UI (Tool Window, `JBList`, `JBSplitter`, `JEditorPane`) |
| Auth | GitHub PAT stored via IDE `PasswordSafe` |
| JVM | Java 21 |

---

## Building from Source

### Prerequisites

- JDK 21+
- Gradle 9.4.1 (included via wrapper)

### Build

```bash
./gradlew buildPlugin
```

The plugin distribution (`.zip`) will be in `build/distributions/`.

### Run in Sandbox IDE

```bash
./gradlew runIde
```

### Run Tests

```bash
./gradlew test
```

---

## Project Structure

```
src/main/kotlin/io/orbitrack/idea/
├── actions/          # Toolbar and context menu actions
│   ├── RefreshAction.kt
│   └── CreateIssueAction.kt
├── api/              # GitHub REST API client layer
│   ├── GitHubClient.kt          # Interface
│   ├── OkHttpGitHubClient.kt    # OkHttp implementation
│   └── GitHubModels.kt          # Serializable API response models
├── cache/            # Local JSON cache
│   └── OrbiCacheManager.kt
├── llm/              # LLM integration (v2 — reserved)
├── model/            # Core data models
│   └── OrbiItem.kt
├── services/         # IDE services
│   ├── OrbiTrackAppService.kt       # App-level: PAT storage, client factory
│   ├── OrbiTrackProjectService.kt   # Project-level: sync, data, actions
│   └── GitRepoDetector.kt           # Auto-detect GitHub repos
├── settings/         # Plugin settings UI
│   └── OrbiTrackConfigurable.kt
├── sync/             # Background sync engine (v2 — reserved)
└── ui/               # Tool window and panels
    ├── OrbiTrackToolWindowFactory.kt
    ├── OrbiTrackPanel.kt
    ├── FilterPanel.kt
    ├── ItemDetailPanel.kt
    ├── ItemCellRenderer.kt
    ├── ListEntry.kt
    └── MarkdownRenderer.kt
```

---

## Roadmap

### v1 — Current
- [x] Plugin scaffold and build configuration
- [x] GitHub PAT auth with credential store
- [x] GitHub API client (issues, PRs, comments, timeline, merge)
- [x] JSON cache with incremental sync
- [x] Auto-detection of GitHub repos from project
- [x] Tool window with unified list and filters
- [x] Detail panel with markdown rendering
- [x] Comment CRUD (create, edit, delete)
- [x] LLM context extraction to clipboard
- [x] PR management (merge, checkout)
- [x] Issue creation
- [x] Settings UI
- [x] GitHub Actions CI/CD
- [ ] Background auto-sync with configurable interval
- [ ] First-run balloon notification (Track All / Select / Ignore)

### v2 — Planned
- [ ] LLM integration (BYOK) — Anthropic, OpenAI, Copilot, Ollama
- [ ] Handwritten note → LLM-drafted comment → one-click post
- [ ] GitHub App authentication (replace PAT for teams)
- [ ] Duplicate detection via local embeddings
- [ ] Auto-label suggestions
- [ ] Keyboard-driven triage mode
- [ ] Bulk operations (close, label, assign)
- [ ] JetBrains Marketplace listing
- [ ] MCP server for external LLM tool access

---

## License

This project is licensed under the Apache License 2.0 — see the [LICENSE](LICENSE) file for details.
