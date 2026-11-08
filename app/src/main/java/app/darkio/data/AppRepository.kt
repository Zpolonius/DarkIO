package app.darkio.data

import android.content.Context
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** [info] er null kun i tests med falske apps. */
data class AppEntry(val id: String, val label: String, val info: LauncherActivityInfo? = null)

fun appId(packageName: String, activity: String, userSerial: Long): String =
    "$packageName/$activity#$userSerial"

/** Launchable apps of the current profile. Nothing is persisted. */
class AppRepository(context: Context, private val scope: CoroutineScope) {
    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val userManager = context.getSystemService(UserManager::class.java)
    private val _apps = MutableStateFlow<List<AppEntry>>(emptyList())
    val apps: StateFlow<List<AppEntry>> = _apps
    private var job: Job? = null

    private val callback = object : LauncherApps.Callback() {
        override fun onPackageAdded(packageName: String, user: UserHandle) = refresh()
        override fun onPackageRemoved(packageName: String, user: UserHandle) = refresh()
        override fun onPackageChanged(packageName: String, user: UserHandle) = refresh()
        override fun onPackagesAvailable(packageNames: Array<String>, user: UserHandle, replacing: Boolean) = refresh()
        override fun onPackagesUnavailable(packageNames: Array<String>, user: UserHandle, replacing: Boolean) = refresh()
    }

    fun start() {
        launcherApps.registerCallback(callback)
        refresh()
    }

    fun stop() = launcherApps.unregisterCallback(callback)

    fun launch(app: AppEntry) {
        val info = app.info ?: return
        scope.launch(Dispatchers.IO) { launcherApps.startMainActivity(info.componentName, info.user, null, null) }
    }

    private fun refresh() {
        job?.cancel()
        job = scope.launch {
            val list = withContext(Dispatchers.IO) {
                val user = Process.myUserHandle()
                val serial = userManager.getSerialNumberForUser(user)
                launcherApps.getActivityList(null, user)
                    .map { AppEntry(appId(it.applicationInfo.packageName, it.componentName.className, serial), it.label.toString(), it) }
                    .sortedWith(appOrder)
            }
            _apps.value = list
        }
    }
}
