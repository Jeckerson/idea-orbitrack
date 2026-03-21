package io.orbitrack.idea.ui

import io.orbitrack.idea.model.OrbiItem

sealed class ListEntry {
    data class Header(val title: String) : ListEntry()
    data class Item(val item: OrbiItem) : ListEntry()
}

enum class GroupMode(val label: String) {
    PLAIN("Plain list"),
    BY_ORG("Group by Org"),
    BY_REPO("Group by Repo"),
    BY_TYPE("Group by Type");

    override fun toString() = label
}

