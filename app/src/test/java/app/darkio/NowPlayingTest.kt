package app.darkio

import android.media.session.PlaybackState
import app.darkio.data.NowPlaying
import app.darkio.data.SessionSnapshot
import app.darkio.data.nowPlayingOf
import org.junit.Assert.assertEquals
import org.junit.Test

/** F25: MediaSession-værdier bliver til kortets tilstand. */
class NowPlayingTest {
    private fun session(title: String? = "Titel", artist: String? = "Kunstner", state: Int = PlaybackState.STATE_PLAYING) =
        SessionSnapshot(title, artist, "Spotify", state)

    @Test
    fun noSessionMeansIdle() {
        assertEquals(NowPlaying.Idle, nowPlayingOf(null))
    }

    @Test
    fun playingSessionShowsTitleArtistAndApp() {
        assertEquals(NowPlaying.Playing("Titel", "Kunstner", "Spotify", true), nowPlayingOf(session()))
    }

    @Test
    fun pausedSessionIsNotPlaying() {
        assertEquals(NowPlaying.Playing("Titel", "Kunstner", "Spotify", false), nowPlayingOf(session(state = PlaybackState.STATE_PAUSED)))
    }

    @Test
    fun bufferingCountsAsPlaying() {
        assertEquals(true, (nowPlayingOf(session(state = PlaybackState.STATE_BUFFERING)) as NowPlaying.Playing).isPlaying)
    }

    @Test
    fun missingMetadataGivesEmptyTextAndStoppedWithoutTitleIsIdle() {
        assertEquals(NowPlaying.Playing("", "", "Spotify", true), nowPlayingOf(session(title = null, artist = null)))
        assertEquals(NowPlaying.Idle, nowPlayingOf(session(title = " ", artist = null, state = PlaybackState.STATE_STOPPED)))
        assertEquals(NowPlaying.Idle, nowPlayingOf(session(title = null, artist = null, state = PlaybackState.STATE_NONE)))
    }
}
