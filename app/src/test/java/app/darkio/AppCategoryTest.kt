package app.darkio

import android.content.pm.ApplicationInfo
import app.darkio.data.AppCategory
import app.darkio.data.appCategoryOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppCategoryTest {
    @Test
    fun mapsApplicationInfoCategories() {
        assertEquals(AppCategory.GAME, appCategoryOf(ApplicationInfo.CATEGORY_GAME))
        assertEquals(AppCategory.SOCIAL, appCategoryOf(ApplicationInfo.CATEGORY_SOCIAL))
        assertEquals(AppCategory.PHOTO, appCategoryOf(ApplicationInfo.CATEGORY_IMAGE))
        assertEquals(AppCategory.PHOTO, appCategoryOf(ApplicationInfo.CATEGORY_VIDEO))
        assertEquals(AppCategory.WORK, appCategoryOf(ApplicationInfo.CATEGORY_PRODUCTIVITY))
        assertEquals(AppCategory.TOOL, appCategoryOf(ApplicationInfo.CATEGORY_MAPS))
        assertEquals(AppCategory.TOOL, appCategoryOf(ApplicationInfo.CATEGORY_ACCESSIBILITY))
        listOf(ApplicationInfo.CATEGORY_UNDEFINED, ApplicationInfo.CATEGORY_AUDIO, ApplicationInfo.CATEGORY_NEWS, -100, 999).forEach {
            assertEquals(AppCategory.OTHER, appCategoryOf(it))
        }
        assertEquals(AppCategory.OTHER, appCategoryOf(null as ApplicationInfo?))
    }

    @Test
    fun sevenCategoriesWithUniqueSlugs() {
        assertEquals(setOf("alle", "social", "spil", "foto", "arbejde", "værktøj", "andet"), AppCategory.entries.map { it.slug }.toSet())
        assertTrue(AppCategory.entries.all { it.labelRes != 0 })
    }
}
