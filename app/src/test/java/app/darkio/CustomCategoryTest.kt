package app.darkio

import app.darkio.data.AppCategory
import app.darkio.data.AppEntry
import app.darkio.data.categoryFromSlug
import app.darkio.data.withCustomCategories
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CustomCategoryTest {
    private val a = AppEntry("a/A#0", "A", category = AppCategory.GAME)
    private val b = AppEntry("b/B#0", "B", category = AppCategory.OTHER)

    @Test
    fun slugMapsToSixSelectableCategories() {
        AppCategory.entries.filter { it != AppCategory.ALL }.forEach { assertEquals(it, categoryFromSlug(it.slug)) }
    }

    @Test
    fun nullUnknownAndAllFallBackToNull() {
        listOf(null, "", "ukendt", "ALL", "alle", "Arbejde ").forEach { assertNull(it, categoryFromSlug(it)) }
    }

    @Test
    fun customCategoryOverridesAutoAndOthersKeepAuto() {
        val result = withCustomCategories(listOf(a, b), mapOf(b.id to "arbejde"))
        assertEquals(listOf(AppCategory.GAME, AppCategory.WORK), result.map { it.category })
    }

    @Test
    fun invalidCustomCategoryKeepsAutoCategory() {
        val result = withCustomCategories(listOf(a, b), mapOf(a.id to "findes-ikke", b.id to "alle"))
        assertEquals(listOf(AppCategory.GAME, AppCategory.OTHER), result.map { it.category })
    }
}
