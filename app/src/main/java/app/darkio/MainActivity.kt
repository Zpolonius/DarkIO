package app.darkio

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import android.graphics.Color
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.StrictMode
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import app.darkio.data.AppDatabase
import app.darkio.data.AppRepository
import app.darkio.data.darkIoPrefs
import app.darkio.data.ensureFyraftenZenRule
import app.darkio.data.expandNotificationPanel
import app.darkio.data.hintSeen
import app.darkio.data.isNotificationPolicyAccessGranted
import app.darkio.data.isWorkHours
import app.darkio.data.markHintSeen
import app.darkio.data.markWorkRowSeeded
import app.darkio.data.resetPreferences
import app.darkio.data.setFyraftenZenRuleActive
import app.darkio.data.Settings
import app.darkio.data.updateSettings
import app.darkio.data.workRowSeeded
import app.darkio.data.workShortcutsSeed
import app.darkio.data.settings as settingsFlow
import app.darkio.ui.theme.DarkIOColors
import app.darkio.home.HomeScreen
import java.time.ZonedDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var apps: AppRepository
    private var homeSignal by mutableIntStateOf(0)
    private var settings by mutableStateOf<Settings?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        if ((applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .detectAll()
                    .penaltyLog()
                    .build()
            )
            StrictMode.setVmPolicy(
                StrictMode.VmPolicy.Builder()
                    .detectAll()
                    .penaltyLog()
                    .build()
            )
        }
        // Mørk tone bag systemlinjerne, så ikoner er lyse også når systemet er i lyst tema (N8).
        enableEdgeToEdge(SystemBarStyle.dark(Color.TRANSPARENT), SystemBarStyle.dark(Color.TRANSPARENT))
        super.onCreate(savedInstanceState)
        apps = AppRepository(this, lifecycleScope, AppDatabase.open(this).choices())
        // Accent og kort sættes, før første billede tegnes (indtil da vises kun bg).
        lifecycleScope.launch {
            darkIoPrefs.settingsFlow().collect {
                DarkIOColors.Accent = it.accent.color
                val prev = settings
                settings = it
                if (prev == null) {
                    checkZenRuleOnResume(it)
                }
            }
        }
        // Seeder arbejdsrækken én gang når apps er indlæst (F23).
        lifecycleScope.launch(Dispatchers.IO) {
            combine(darkIoPrefs.workRowSeeded(), apps.lists) { seeded, lists ->
                seeded to (lists.visible + lists.hidden)
            }.collect { (seeded, allApps) ->
                if (!seeded && allApps.isNotEmpty()) {
                    val seed = workShortcutsSeed(allApps)
                    if (seed.isNotEmpty()) {
                        AppDatabase.open(this@MainActivity).choices().setShortcuts(seed, emptyList())
                    }
                    darkIoPrefs.markWorkRowSeeded()
                }
            }
        }
        setContent {
            val current = settings
            if (current == null) {
                // Uden gemte indstillinger endnu: mørkt i stedet for en utonet baggrund.
                Box(Modifier.fillMaxSize().background(DarkIOColors.Bg))
                return@setContent
            }
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
                onToggleMedia = apps::setMediaApp,
                onSetCategory = apps::setCategory,
                onAppInfo = apps::openAppInfo,
                onUninstall = apps::uninstall,
                homeSignal = homeSignal,
                settings = current,
                onSettingsChange = { lifecycleScope.launch { darkIoPrefs.updateSettings(it) } },
                onResetAll = {
                    lifecycleScope.launch(Dispatchers.IO) {
                        AppDatabase.open(this@MainActivity).resetAll()
                        darkIoPrefs.resetPreferences()
                    }
                },
                onOpenNotifications = { lifecycleScope.launch(Dispatchers.Default) { expandNotificationPanel(this@MainActivity) } },
                onFyraftenZenRule = { active ->
                    lifecycleScope.launch(Dispatchers.IO) {
                        if (isNotificationPolicyAccessGranted(this@MainActivity)) {
                            val ruleId = ensureFyraftenZenRule(this@MainActivity, settings?.workZenRuleId)
                            if (ruleId != null) {
                                if (ruleId != settings?.workZenRuleId) {
                                    darkIoPrefs.updateSettings { it.copy(workZenRuleId = ruleId) }
                                }
                                setFyraftenZenRuleActive(this@MainActivity, ruleId, active)
                            }
                        }
                    }
                },
            )
        }
    }

    private fun checkZenRuleOnResume(current: Settings) {
        val now = ZonedDateTime.now()
        if (isWorkHours(current, now) && isNotificationPolicyAccessGranted(this)) {
            lifecycleScope.launch(Dispatchers.IO) {
                val ruleId = current.workZenRuleId ?: ensureFyraftenZenRule(this@MainActivity, null)
                if (ruleId != null) {
                    if (ruleId != current.workZenRuleId) {
                        darkIoPrefs.updateSettings { it.copy(workZenRuleId = ruleId) }
                    }
                    setFyraftenZenRuleActive(this@MainActivity, ruleId, false)
                }
            }
        }
    }

    // Hjem-knappen på en kørende launcher leverer ny intent (singleTask): vis side 1.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME)) homeSignal++
    }

    override fun onResume() {
        super.onResume()
        settings?.let { checkZenRuleOnResume(it) }
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
