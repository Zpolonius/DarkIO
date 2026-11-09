package app.darkio

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
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
                onSetCategory = apps::setCategory,
                onAppInfo = apps::openAppInfo,
                onUninstall = apps::uninstall,
            )
        }
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
