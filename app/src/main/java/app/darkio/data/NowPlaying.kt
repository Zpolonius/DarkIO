package app.darkio.data

import android.media.session.PlaybackState

/** Det "Spiller nu"-kortet viser: ingen adgang, intet aktivt, eller en aktiv medie-session. */
sealed interface NowPlaying {
    data object NoAccess : NowPlaying
    data object Idle : NowPlaying
    data class Playing(val title: String, val artist: String, val appLabel: String, val isPlaying: Boolean) : NowPlaying
}

/** De rene værdier fra første aktive MediaSession; [playbackState] er et PlaybackState.STATE_*-tal. */
data class SessionSnapshot(val title: String?, val artist: String?, val appLabel: String, val playbackState: Int)

fun nowPlayingOf(session: SessionSnapshot?): NowPlaying {
    if (session == null) return NowPlaying.Idle
    val ended = session.playbackState == PlaybackState.STATE_NONE || session.playbackState == PlaybackState.STATE_STOPPED
    if (ended && session.title.isNullOrBlank()) return NowPlaying.Idle
    val playing = session.playbackState == PlaybackState.STATE_PLAYING || session.playbackState == PlaybackState.STATE_BUFFERING
    return NowPlaying.Playing(session.title.orEmpty(), session.artist.orEmpty(), session.appLabel, playing)
}
