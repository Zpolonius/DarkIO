package app.darkio

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import app.darkio.data.AppEntry
import app.darkio.data.AppIconCache
import app.darkio.drawer.AppDrawer
import app.darkio.drawer.AppIcon
import org.junit.Rule
import org.junit.Test

class AppIconCacheUiTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun appIconReadsSynchronouslyFromCacheOnFirstFrame() {
        val app = AppEntry("test.app/Main#0", "TestApp")
        val cache = AppIconCache(maxSize = 10)
        val density = rule.density.density
        val px = (48 * density).toInt().coerceAtLeast(1)

        val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888).asImageBitmap()
        cache.put(app.id, px, bmp)

        rule.setContent {
            AppIcon(app = app, size = 48.dp, cache = cache)
        }

        // Første frame har ikonet på plads, fordi AppIcon læser synkront fra cachen som initial værdi
        rule.onNodeWithTag("appIconBitmap:${app.id}").assertIsDisplayed()
    }

    @Test
    fun drawerShowsIconsOnFirstFrameWhenCacheIsPopulated() {
        val apps = (1..8).map { AppEntry("test.app$it/Main#0", "App $it") }
        val density = rule.density.density
        val px = (56 * density).toInt().coerceAtLeast(1)

        try {
            apps.forEach { app ->
                val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888).asImageBitmap()
                AppIconCache.put(app.id, px, bmp)
            }

            rule.setContent {
                AppDrawer(
                    apps = apps,
                    onLaunch = {},
                    onMenu = {},
                    onDismiss = {},
                )
            }

            // Første frame efter åbning af skuffen har ikoner for de synlige apps
            apps.take(4).forEach { app ->
                rule.onNodeWithTag("appIconBitmap:${app.id}").assertIsDisplayed()
            }
        } finally {
            AppIconCache.clear()
        }
    }
}
