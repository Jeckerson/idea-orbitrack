package io.orbitrack.idea.ui

import io.orbitrack.idea.model.OrbiItem

sealed class ListEntry {
    data class Header(val title: String, val depth: Int = 0) : ListEntry()
    data class Item(val item: OrbiItem, val depth: Int = 0) : ListEntry()
}

/**
 * Grouping levels in canonical order. Users can pick any combination;
 * the hierarchy always follows: BY_ORG → BY_REPO → BY_TYPE.
 */
enum class GroupMode(val label: String) {
    BY_ORG("By Org"),
    BY_REPO("By Repo"),
    BY_TYPE("By Type");

    override fun toString() = label
}

enum class SortField(val label: String) {
    UPDATED("Last Updated"),
    CREATED("Created Date"),
    ID("ID");

    override fun toString() = label
}

enum class SortDirection(val label: String, val symbol: String) {
    DESC("Descending", "\u2193"),   // ↓
    ASC("Ascending", "\u2191");     // ↑

    override fun toString() = label
}

