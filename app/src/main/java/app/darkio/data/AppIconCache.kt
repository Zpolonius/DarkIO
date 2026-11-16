package app.darkio.data

import androidx.collection.LruCache
import androidx.compose.ui.graphics.ImageBitmap

/** Nøgle til app-ikoner i hukommelsen: app-id + størrelse i pixels. */
data class AppIconKey(val appId: String, val size: Int)

const val DEFAULT_APP_ICON_CACHE_SIZE = 300

/**
 * Cache for app-ikoner i hukommelsen med en fast øvre grænse (LRU).
 * Nøgle er app-id + størrelse. Trådsikker så IO-tråd kan fylde cachen og UI læse synkront.
 */
open class AppIconCache(val maxSize: Int = DEFAULT_APP_ICON_CACHE_SIZE) {
    companion object : AppIconCache(DEFAULT_APP_ICON_CACHE_SIZE)

    private val lock = Any()
    private val lru = LruCache<AppIconKey, ImageBitmap>(maxSize)

    val size: Int get() = synchronized(lock) { lru.size() }

    open fun get(key: AppIconKey): ImageBitmap? = synchronized(lock) { lru.get(key) }
    open fun get(appId: String, size: Int): ImageBitmap? = get(AppIconKey(appId, size))

    open fun put(key: AppIconKey, bitmap: ImageBitmap) {
        synchronized(lock) { lru.put(key, bitmap) }
    }
    open fun put(appId: String, size: Int, bitmap: ImageBitmap) = put(AppIconKey(appId, size), bitmap)

    open fun remove(key: AppIconKey): ImageBitmap? = synchronized(lock) { lru.remove(key) }

    open fun remove(appId: String) = synchronized(lock) {
        val keys = lru.snapshot().keys.filter { it.appId == appId }
        for (k in keys) {
            lru.remove(k)
        }
    }

    open fun removePackage(packageName: String) = synchronized(lock) {
        val prefix = "$packageName/"
        val keys = lru.snapshot().keys.filter { it.appId.startsWith(prefix) || it.appId == packageName }
        for (k in keys) {
            lru.remove(k)
        }
    }

    open fun clear() = synchronized(lock) { lru.evictAll() }
}
