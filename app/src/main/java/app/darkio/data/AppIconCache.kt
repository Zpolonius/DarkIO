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

    fun get(key: AppIconKey): ImageBitmap? = synchronized(lock) { lru.get(key) }
    fun get(appId: String, size: Int): ImageBitmap? = get(AppIconKey(appId, size))

    fun put(key: AppIconKey, bitmap: ImageBitmap) {
        synchronized(lock) { lru.put(key, bitmap) }
    }
    fun put(appId: String, size: Int, bitmap: ImageBitmap) = put(AppIconKey(appId, size), bitmap)

    fun removePackage(packageName: String) = synchronized(lock) {
        val prefix = "$packageName/"
        val keys = lru.snapshot().keys.filter { it.appId.startsWith(prefix) || it.appId == packageName }
        for (k in keys) {
            lru.remove(k)
        }
    }

    fun clear() = synchronized(lock) { lru.evictAll() }
}
