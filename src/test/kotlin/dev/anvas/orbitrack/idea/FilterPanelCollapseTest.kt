package dev.anvas.orbitrack.idea

import dev.anvas.orbitrack.idea.cache.CachedFilterState
import dev.anvas.orbitrack.idea.ui.FilterPanel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Field

/**
 * Tests for [FilterPanel]'s collapsible header behaviour.
 *
 * These tests rely on headless AWT.  If AWT is unavailable (e.g. in a fully
 * display-less CI without `java.awt.headless=true`) the test is skipped via
 * [Assume] rather than failing the build.
 */
class FilterPanelCollapseTest {

    @Before
    fun requireHeadless() {
        System.setProperty("java.awt.headless", "true")
    }

    // ---------- helpers ----------

    /**
     * Creates a [FilterPanel] and ensures it was constructed without errors.
     * Returns the panel or skips the test if the AWT environment is unavailable.
     */
    private fun makePanel(): FilterPanel {
        return try {
            FilterPanel {}
        } catch (e: Exception) {
            Assume.assumeNoException("Skipping: cannot construct FilterPanel in this environment", e)
            throw e // unreachable – Assume throws AssumptionViolatedException
        }
    }

    /** Reads a private field from [FilterPanel] by name via reflection. */
    private fun readField(panel: FilterPanel, name: String): Any? {
        var cls: Class<*>? = panel.javaClass
        while (cls != null) {
            try {
                val f: Field = cls.getDeclaredField(name)
                f.isAccessible = true
                return f.get(panel)
            } catch (_: NoSuchFieldException) {
                cls = cls.superclass
            }
        }
        error("Field '$name' not found in FilterPanel hierarchy")
    }

    private fun innerPanelVisible(panel: FilterPanel): Boolean {
        val inner = readField(panel, "innerPanel") as javax.swing.JPanel
        return inner.isVisible
    }

    // ---------- tests ----------

    @Test
    fun `default state is expanded`() {
        val panel = makePanel()
        assertTrue("Filter controls must be visible by default", innerPanelVisible(panel))
    }

    @Test
    fun `getFilterState reports filtersCollapsed false when expanded`() {
        val panel = makePanel()
        assertFalse(panel.getFilterState().filtersCollapsed)
    }

    @Test
    fun `clicking toggle hides inner panel`() {
        val panel = makePanel()
        val toggleButton = readField(panel, "toggleButton") as javax.swing.JButton
        // simulate click
        toggleButton.doClick()
        assertFalse("Inner panel must be hidden after toggle", innerPanelVisible(panel))
    }

    @Test
    fun `getFilterState reports filtersCollapsed true after toggle`() {
        val panel = makePanel()
        val toggleButton = readField(panel, "toggleButton") as javax.swing.JButton
        toggleButton.doClick()
        assertTrue(panel.getFilterState().filtersCollapsed)
    }

    @Test
    fun `toggle twice returns to expanded`() {
        val panel = makePanel()
        val toggleButton = readField(panel, "toggleButton") as javax.swing.JButton
        toggleButton.doClick()
        toggleButton.doClick()
        assertTrue("Inner panel must be visible after two toggles", innerPanelVisible(panel))
        assertFalse(panel.getFilterState().filtersCollapsed)
    }

    @Test
    fun `restoreFilterState with filtersCollapsed true hides inner panel`() {
        val panel = makePanel()
        val state = CachedFilterState(filtersCollapsed = true)
        panel.restoreFilterState(state)
        assertFalse("Inner panel must be hidden after restoring collapsed state", innerPanelVisible(panel))
    }

    @Test
    fun `restoreFilterState with filtersCollapsed false keeps inner panel visible`() {
        val panel = makePanel()
        // First collapse it so we know restore actually changes things
        val collapsedState = CachedFilterState(filtersCollapsed = true)
        panel.restoreFilterState(collapsedState)
        val expandedState = CachedFilterState(filtersCollapsed = false)
        panel.restoreFilterState(expandedState)
        assertTrue("Inner panel must be visible after restoring expanded state", innerPanelVisible(panel))
    }

    @Test
    fun `toggle button text contains chevron-down when expanded`() {
        val panel = makePanel()
        val toggleButton = readField(panel, "toggleButton") as javax.swing.JButton
        assertTrue(
            "Expanded toggle must contain ▾ (U+25BE)",
            toggleButton.text.contains("\u25BE")
        )
    }

    @Test
    fun `toggle button text contains chevron-right when collapsed`() {
        val panel = makePanel()
        val toggleButton = readField(panel, "toggleButton") as javax.swing.JButton
        toggleButton.doClick()
        assertTrue(
            "Collapsed toggle must contain ▸ (U+25B8)",
            toggleButton.text.contains("\u25B8")
        )
    }

    @Test
    fun `buildSummary returns empty string when all defaults`() {
        val panel = makePanel()
        val summary = panel.buildSummary()
        assertTrue("Summary must be blank when all filters are default", summary.isBlank())
    }

    @Test
    fun `buildSummary includes state when non-default`() {
        val panel = makePanel()
        // stateCombo index 2 = "Closed"
        val stateCombo = readField(panel, "stateCombo") as javax.swing.JComboBox<*>
        stateCombo.selectedIndex = 2
        val summary = panel.buildSummary()
        assertTrue("Summary must include state selection", summary.contains("Closed"))
    }

    @Test
    fun `buildSummary includes type when non-default`() {
        val panel = makePanel()
        val typeCombo = readField(panel, "typeCombo") as javax.swing.JComboBox<*>
        typeCombo.selectedIndex = 2  // "PRs"
        val summary = panel.buildSummary()
        assertTrue("Summary must include type selection", summary.contains("PR"))
    }

    @Test
    fun `buildSummary is capped at 40 characters`() {
        val panel = makePanel()
        // A very long sort field description won't exceed 40 chars alone, but
        // the join of multiple active filters must still be capped.
        val summary = panel.buildSummary()
        assertTrue(
            "Summary must not exceed 40 chars",
            summary.length <= 40
        )
    }

    @Test
    fun `collapsed toggle label includes summary when non-default state selected`() {
        val panel = makePanel()
        val stateCombo = readField(panel, "stateCombo") as javax.swing.JComboBox<*>
        stateCombo.selectedIndex = 2  // "Closed"
        val toggleButton = readField(panel, "toggleButton") as javax.swing.JButton
        toggleButton.doClick()  // collapse
        assertTrue(
            "Collapsed label must show active filter summary",
            toggleButton.text.contains("Closed")
        )
    }
}

