package app.darkio

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import app.darkio.dashboard.CardState
import app.darkio.dashboard.LoadedCard
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Tester: kortet henter kun mens STARTED (N10) og henter igen ved genstart. */
class DashboardLifecycleUiTest {
    @get:Rule
    val rule = createComposeRule()

    private class Owner : LifecycleOwner {
        val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle get() = registry
    }

    @Test
    fun noLoadsWhileStoppedAndReloadsOnStart() {
        val owner = Owner()
        rule.runOnUiThread { owner.registry.currentState = Lifecycle.State.RESUMED }
        var calls = 0
        rule.setContent {
            @Suppress("DEPRECATION")
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                LoadedCard<Int>(load = { calls++; CardState.Content(calls) }, refreshAfterMs = { 100L }) { BasicText("v$it") }
            }
        }
        rule.waitUntil(5_000) { calls >= 3 }
        rule.runOnUiThread { owner.registry.currentState = Lifecycle.State.CREATED }
        rule.mainClock.advanceTimeBy(50)
        val stopped = calls
        rule.mainClock.advanceTimeBy(2_000)
        assertEquals("ingen hentning mens skjult", stopped, calls)
        rule.runOnUiThread { owner.registry.currentState = Lifecycle.State.STARTED }
        rule.waitUntil(5_000) { calls > stopped }
    }
}
