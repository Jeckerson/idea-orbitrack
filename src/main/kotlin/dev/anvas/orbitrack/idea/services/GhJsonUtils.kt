package dev.anvas.orbitrack.idea.services

/**
 * Utility functions for processing raw `gh` CLI JSON output.
 */

/**
 * `gh api --paginate` emits one JSON array per page with no separator between them,
 * e.g. `[...][...]`.  This function joins such output into a single, valid JSON array
 * so that kotlinx.serialization can parse it.
 *
 * Handles:
 *  - Single page: `[...]`  → returned as-is
 *  - Multi-page: `[...][...]` → `[...,...]`
 *  - Empty pages: `[][]`   → `[]`
 *  - Mixed whitespace between arrays
 *  - Non-array input (single object `{...}`) → returned as-is
 */
internal fun normaliseGhPaginatedJson(raw: String): String {
    val trimmed = raw.trim()
    if (!trimmed.startsWith("[")) return trimmed   // single object – pass through
    return trimmed.replace(Regex("""\]\s*\["""), ",")
        .let { merged ->
            // `[][...]` or `[...][` edge cases – collapse empty brackets that result in `[,`
            merged.replace(Regex("""\[,"""), "[")
                  .replace(Regex(""",]"""), "]")
        }
}

