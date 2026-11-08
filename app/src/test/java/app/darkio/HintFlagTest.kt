package app.darkio

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.io.File
import app.darkio.data.hintSeen
import app.darkio.data.markHintSeen
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class HintFlagTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun hintIsNotSeenWhenFlagMissing() = runTest {
        val store = PreferenceDataStoreFactory.create(scope = backgroundScope) { File(folder.root, "unset.preferences_pb") }
        assertFalse(store.hintSeen().first())
    }

    @Test
    fun hintIsSeenAfterMarking() = runTest {
        val store = PreferenceDataStoreFactory.create(scope = backgroundScope) { File(folder.root, "marked.preferences_pb") }
        store.markHintSeen()
        assertTrue(store.hintSeen().first())
    }
}
