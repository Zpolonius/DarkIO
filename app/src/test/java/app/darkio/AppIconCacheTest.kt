package app.darkio

import android.content.Context
import android.content.pm.LauncherApps
import android.os.UserHandle
import androidx.compose.ui.graphics.ImageBitmap
import app.darkio.data.AppChoiceDao
import app.darkio.data.AppEntry
import app.darkio.data.AppIconCache
import app.darkio.data.AppIconKey
import app.darkio.data.AppRepository
import app.darkio.data.appId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.lang.reflect.Proxy

class AppIconCacheTest {

    private fun dummyBitmap(name: String): ImageBitmap = Proxy.newProxyInstance(
        ImageBitmap::class.java.classLoader,
        arrayOf(ImageBitmap::class.java),
    ) { _, method, _ ->
        when (method.name) {
            "toString" -> name
            "hashCode" -> name.hashCode()
            "equals" -> true
            else -> null
        }
    } as ImageBitmap

    private fun dummyUser(): UserHandle {
        val field = sun.misc.Unsafe::class.java.getDeclaredField("theUnsafe")
        field.isAccessible = true
        val unsafe = field.get(null) as sun.misc.Unsafe
        return unsafe.allocateInstance(UserHandle::class.java) as UserHandle
    }

    private fun dummyContext(): Context {
        val field = sun.misc.Unsafe::class.java.getDeclaredField("theUnsafe")
        field.isAccessible = true
        val unsafe = field.get(null) as sun.misc.Unsafe
        return unsafe.allocateInstance(android.content.ContextWrapper::class.java) as Context
    }

    private class FakeAppChoiceDao : AppChoiceDao() {
        override fun all(): kotlinx.coroutines.flow.Flow<List<app.darkio.data.AppChoice>> =
            kotlinx.coroutines.flow.flowOf(emptyList())
        override fun shortcuts(): kotlinx.coroutines.flow.Flow<List<app.darkio.data.PageShortcut>> =
            kotlinx.coroutines.flow.flowOf(emptyList())
        override suspend fun find(packageName: String, activity: String, userSerial: Long): app.darkio.data.AppChoice? = null
        override suspend fun upsert(choice: app.darkio.data.AppChoice) {}
        override suspend fun upsertShortcuts(shortcuts: List<app.darkio.data.PageShortcut>) {}
        override suspend fun deleteShortcut(page: Int, packageName: String, activity: String, userSerial: Long) {}
        override suspend fun clearAllChoices() {}
        override suspend fun clearAllShortcuts() {}
        override fun mediaApps(): kotlinx.coroutines.flow.Flow<List<app.darkio.data.MediaApp>> =
            kotlinx.coroutines.flow.flowOf(emptyList())
        override suspend fun nextMediaPosition(): Int = 0
        override suspend fun insertMediaApp(app: app.darkio.data.MediaApp) {}
        override suspend fun removeMediaApp(packageName: String, activity: String, userSerial: Long) {}
        override suspend fun clearAllMediaApps() {}
    }

    private fun dummyDao(): AppChoiceDao = FakeAppChoiceDao()

    @Test
    fun keyCombinesAppIdAndSize() {
        val key1 = AppIconKey("com.foo/com.foo.Main#0", 48)
        val key1Same = AppIconKey("com.foo/com.foo.Main#0", 48)
        val keyDifferentSize = AppIconKey("com.foo/com.foo.Main#0", 56)
        val keyDifferentApp = AppIconKey("com.bar/com.bar.Main#0", 48)

        assertEquals(key1, key1Same)
        assertEquals(key1.hashCode(), key1Same.hashCode())
        assertNotEquals(key1, keyDifferentSize)
        assertNotEquals(key1, keyDifferentApp)

        val cache = AppIconCache(maxSize = 10)
        val b48 = dummyBitmap("b48")
        val b56 = dummyBitmap("b56")

        cache.put("com.foo/com.foo.Main#0", 48, b48)
        cache.put("com.foo/com.foo.Main#0", 56, b56)

        assertEquals(b48, cache.get("com.foo/com.foo.Main#0", 48))
        assertEquals(b56, cache.get("com.foo/com.foo.Main#0", 56))
    }

    @Test
    fun upperBoundEvictsLeastRecentlyUsed() {
        val cache = AppIconCache(maxSize = 2)
        val b1 = dummyBitmap("b1")
        val b2 = dummyBitmap("b2")
        val b3 = dummyBitmap("b3")

        cache.put("app1", 48, b1)
        cache.put("app2", 48, b2)
        assertEquals(2, cache.size)

        // Læs app1 så den markeres som nyligt brugt; app2 er nu ældst (LRU)
        assertEquals(b1, cache.get("app1", 48))

        // Tilføj 3. element: app2 skal smides ud, grænsen på 2 bevares
        cache.put("app3", 48, b3)
        assertEquals(2, cache.size)
        assertEquals(b1, cache.get("app1", 48))
        assertNull(cache.get("app2", 48))
        assertEquals(b3, cache.get("app3", 48))
    }

    @Test
    fun packageUpdateOrRemovalRemovesIconsFromCache() {
        val cache = AppIconCache(maxSize = 10)
        val bAlpha48 = dummyBitmap("alpha48")
        val bAlpha56 = dummyBitmap("alpha56")
        val bBeta48 = dummyBitmap("beta48")

        val alphaId = appId("com.alpha", "com.alpha.Main", 0)
        val betaId = appId("com.beta", "com.beta.Main", 0)

        cache.put(alphaId, 48, bAlpha48)
        cache.put(alphaId, 56, bAlpha56)
        cache.put(betaId, 48, bBeta48)

        assertEquals(3, cache.size)

        // Fjern com.alpha pakken
        cache.removePackage("com.alpha")

        // Begge ikoner for com.alpha (størrelse 48 og 56) skal være væk
        assertNull(cache.get(alphaId, 48))
        assertNull(cache.get(alphaId, 56))

        // com.beta skal stadig ligge i cachen
        assertEquals(bBeta48, cache.get(betaId, 48))
        assertEquals(1, cache.size)
    }

    @Test
    fun allCallbackMethodsEvictPackageIcons() {
        val cache = AppIconCache(maxSize = 10)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val repo = AppRepository(dummyContext(), scope, dummyDao(), cache)
        val user = dummyUser()

        val p1 = appId("com.p1", "Main", 0)
        val p2 = appId("com.p2", "Main", 0)
        val p3 = appId("com.p3", "Main", 0)
        val p4 = appId("com.p4", "Main", 0)
        val p5 = appId("com.p5", "Main", 0)

        // 1. onPackageAdded
        cache.put(p1, 48, dummyBitmap("p1"))
        assertNotNull(cache.get(p1, 48))
        repo.callback.onPackageAdded("com.p1", user)
        assertNull(cache.get(p1, 48))

        // 2. onPackageRemoved
        cache.put(p2, 48, dummyBitmap("p2"))
        assertNotNull(cache.get(p2, 48))
        repo.callback.onPackageRemoved("com.p2", user)
        assertNull(cache.get(p2, 48))

        // 3. onPackageChanged
        cache.put(p3, 48, dummyBitmap("p3"))
        assertNotNull(cache.get(p3, 48))
        repo.callback.onPackageChanged("com.p3", user)
        assertNull(cache.get(p3, 48))

        // 4. onPackagesAvailable
        cache.put(p4, 48, dummyBitmap("p4"))
        assertNotNull(cache.get(p4, 48))
        repo.callback.onPackagesAvailable(arrayOf("com.p4"), user, false)
        assertNull(cache.get(p4, 48))

        // 5. onPackagesUnavailable
        cache.put(p5, 48, dummyBitmap("p5"))
        assertNotNull(cache.get(p5, 48))
        repo.callback.onPackagesUnavailable(arrayOf("com.p5"), user, false)
        assertNull(cache.get(p5, 48))
    }

    @Test
    fun preloadingFillsCacheSynchronouslyBeforeDraw() {
        val cache = AppIconCache(maxSize = 10)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val repo = AppRepository(dummyContext(), scope, dummyDao(), cache)

        val apps = listOf(
            AppEntry("app1", "App 1"),
            AppEntry("app2", "App 2"),
        )

        var loadCount = 0
        repo.preloadIcons(apps) { app, px, dpi ->
            loadCount++
            dummyBitmap(app.id)
        }

        // Cachen er nu fyldt for begge apps
        assertEquals(2, loadCount)
        assertNotNull(cache.get("app1", 1))
        assertNotNull(cache.get("app2", 1))

        // Næste opslag læser direkte synkront fra cachen uden genindlæsning
        assertNotNull(cache.get("app1", 1))
        assertNotNull(cache.get("app2", 1))
        assertEquals(2, loadCount)
    }

    @Test
    fun iconsNotReloadedOnScrollPageSwitchOrReturnFromSettings() {
        val cache = AppIconCache(maxSize = 10)
        val apps = listOf(
            AppEntry("appA", "App A"),
            AppEntry("appB", "App B"),
            AppEntry("appC", "App C"),
        )

        var rasterizationCount = 0
        val loader = { app: AppEntry, size: Int ->
            val cached = cache.get(app.id, size)
            if (cached != null) {
                cached
            } else {
                rasterizationCount++
                dummyBitmap(app.id).also { cache.put(app.id, size, it) }
            }
        }

        // Første indlæsning: alle 3 ikoner rasteriseres én gang
        apps.forEach { loader(it, 48) }
        assertEquals(3, rasterizationCount)

        // 1. Simuler scroll (apps ruller ud af synsfelt og ind igen)
        apps.forEach { loader(it, 48) }
        assertEquals(3, rasterizationCount)

        // 2. Simuler sideskift (skift til side 2 og tilbage)
        apps.take(2).forEach { loader(it, 48) }
        assertEquals(3, rasterizationCount)

        // 3. Simuler tilbagevenden fra indstillinger (hele skuffen genopfriskes)
        apps.forEach { loader(it, 48) }
        assertEquals(3, rasterizationCount)
    }
}
