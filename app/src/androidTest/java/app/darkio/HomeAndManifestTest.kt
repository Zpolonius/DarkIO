package app.darkio

import android.content.Intent
import android.content.pm.PackageManager
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeAndManifestTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val pm = context.packageManager

    @Test
    fun homeIntentResolvesToDarkIO() {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val found = pm.queryIntentActivities(home, PackageManager.MATCH_DEFAULT_ONLY)
            .any { it.activityInfo.packageName == "app.darkio" && it.activityInfo.name == "app.darkio.MainActivity" }
        assertTrue(found)
    }

    @Test
    fun noInternetNorQueryAllPackages() {
        val perms = pm.getPackageInfo("app.darkio", PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong()))
            .requestedPermissions.orEmpty().toList()
        assertFalse(perms.contains("android.permission.INTERNET"))
        assertFalse(perms.contains("android.permission.QUERY_ALL_PACKAGES"))
        // N4: kun de tilladte tilladelser indtil nu
        assertEquals(
            setOf(
                "android.permission.REQUEST_DELETE_PACKAGES",
                "android.permission.EXPAND_STATUS_BAR",
                "android.permission.READ_CALENDAR",
                "android.permission.health.READ_STEPS",
                "android.permission.health.READ_TOTAL_CALORIES_BURNED",
            ),
            perms.filter { !it.startsWith("app.darkio.") }.toSet(),
        )
    }

    // exported test/tooling activities that exist only in the debug build
    private val debugOnly = setOf("androidx.compose.ui.tooling.PreviewActivity", "androidx.activity.ComponentActivity")

    @Test
    fun onlyMainActivityIsExported() {
        val flags = PackageManager.GET_ACTIVITIES or PackageManager.GET_SERVICES or
            PackageManager.GET_RECEIVERS or PackageManager.GET_PROVIDERS
        val info = pm.getPackageInfo("app.darkio", PackageManager.PackageInfoFlags.of(flags.toLong()))
        val exported = (info.activities.orEmpty().map { it.name to it.exported } +
            info.services.orEmpty().map { it.name to it.exported } +
            info.receivers.orEmpty().map { it.name to it.exported } +
            info.providers.orEmpty().map { it.name to it.exported })
            .filter { it.second }.map { it.first }
            .filterNot { it in debugOnly }
        assertEquals(
            setOf(
                "app.darkio.MainActivity",
                "app.darkio.HealthRationaleActivity",
                "app.darkio.ViewPermissionUsageActivity",
            ),
            exported.toSet(),
        )
    }

    @Test
    fun installedAppsAreListedOnFirstLoad() = kotlinx.coroutines.runBlocking {
        val db = androidx.room.Room.inMemoryDatabaseBuilder(context, app.darkio.data.AppDatabase::class.java).build()
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)
        val repo = app.darkio.data.AppRepository(context, scope, db.choices())
        InstrumentationRegistry.getInstrumentation().runOnMainSync { repo.start() }
        val apps = kotlinx.coroutines.withTimeout(10_000) { repo.lists.first { it.visible.isNotEmpty() }.visible }
        InstrumentationRegistry.getInstrumentation().runOnMainSync { repo.stop() }
        scope.coroutineContext[kotlinx.coroutines.Job]!!.cancel()
        scope.coroutineContext[kotlinx.coroutines.Job]!!.join()
        db.close()
        assertTrue(apps.any { it.id.startsWith("app.darkio/app.darkio.MainActivity#") })
        assertTrue(apps.map { it.id }.distinct().size == apps.size)
    }
}
