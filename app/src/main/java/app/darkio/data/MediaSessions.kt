package app.darkio.data

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import app.darkio.MediaListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/** Kortets tilstand og den controller, knapperne styrer (null uden aktiv session). */
data class MediaNow(val state: NowPlaying, val controller: MediaController? = null)

private fun listener(context: Context) = ComponentName(context, MediaListener::class.java)

fun isMediaAccessGranted(context: Context): Boolean =
    context.getSystemService(NotificationManager::class.java).isNotificationListenerAccessGranted(listener(context))

/** Åbner Androids side, hvor brugeren selv giver DarkIO notifikationsadgang (N4: først ved tryk). */
fun openMediaAccessSettings(context: Context) {
    val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
        .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, listener(context).flattenToString())
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}

/**
 * Første aktive medie-session, så længe flowet samles (kaldes kun mens hjem vises). Lytterne sender kun "læs igen";
 * selve opslaget sker her på IO. Afmelder sig, når flowet stoppes.
 */
fun mediaNowFlow(context: Context): Flow<MediaNow> = flow {
    if (!isMediaAccessGranted(context)) {
        emit(MediaNow(NowPlaying.NoAccess))
        return@flow
    }
    val manager = context.getSystemService(MediaSessionManager::class.java)
    val component = listener(context)
    val main = Handler(Looper.getMainLooper())
    val refresh = Channel<Unit>(Channel.CONFLATED)
    val controllerCallback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) { refresh.trySend(Unit) }
        override fun onMetadataChanged(metadata: MediaMetadata?) { refresh.trySend(Unit) }
        override fun onSessionDestroyed() { refresh.trySend(Unit) }
    }
    val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { refresh.trySend(Unit) }
    var current: MediaController? = null
    try {
        manager.addOnActiveSessionsChangedListener(sessionsListener, component, main)
        refresh.trySend(Unit)
        for (tick in refresh) {
            val first = manager.getActiveSessions(component).firstOrNull()
            if (first?.sessionToken != current?.sessionToken) {
                current?.unregisterCallback(controllerCallback)
                first?.registerCallback(controllerCallback, main)
                current = first
            }
            emit(MediaNow(nowPlayingOf(current?.let { snapshotOf(context, it) }), current))
        }
    } catch (_: SecurityException) {
        emit(MediaNow(NowPlaying.NoAccess))
    } finally {
        runCatching { manager.removeOnActiveSessionsChangedListener(sessionsListener) }
        current?.unregisterCallback(controllerCallback)
    }
}.flowOn(Dispatchers.IO)

private fun snapshotOf(context: Context, controller: MediaController): SessionSnapshot {
    val meta = controller.metadata
    val pm = context.packageManager
    val app = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(controller.packageName, 0)).toString() }.getOrDefault(controller.packageName)
    val artist = meta?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: meta?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST) ?: meta?.getString(MediaMetadata.METADATA_KEY_ALBUM)
    return SessionSnapshot(meta?.getString(MediaMetadata.METADATA_KEY_TITLE), artist, app, controller.playbackState?.state ?: PlaybackState.STATE_NONE)
}
