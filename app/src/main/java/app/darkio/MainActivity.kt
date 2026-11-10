package app.darkio

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import android.content.Intent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import app.darkio.data.AppDatabase
import app.darkio.data.AppRepository
import app.darkio.data.darkIoPrefs
import app.darkio.data.hintSeen
import app.darkio.data.markHintSeen
import app.darkio.home.HomeScreen
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var apps: AppRepository
    private var homeSignal by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        apps = AppRepository(this, lifecycleScope, AppDatabase.open(this).choices())
        setContent {
            val list = apps.lists.collectAsState().value
            val hintSeen = darkIoPrefs.hintSeen().collectAsState(initial = true).value
            HomeScreen(
                apps = list,
                hintSeen = hintSeen,
                onHintSeen = { lifecycleScope.launch { darkIoPrefs.markHintSeen() } },
                onLaunch = apps::launch,
                onSetHidden = apps::setHidden,
                onSetFavorites = apps::setFavoriteSlots,
                onSetShortcuts = apps::setShortcutSlots,
                onSetCategory = apps::setCategory,
                onAppInfo = apps::openAppInfo,
                onUninstall = apps::uninstall,
                homeSignal = homeSignal,
            )
        }
    }

    // Hjem-knappen på en kørende launcher leverer ny intent (singleTask): vis side 1.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME)) homeSignal++
    }

    override fun onStart() {
        super.onStart()
        apps.start()
    }

    override fun onStop() {
        apps.stop()
        super.onStop()
    }
}
