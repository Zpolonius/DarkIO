package app.darkio.data

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.net.Uri
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** [info] er null kun i tests med falske apps. */
data class AppEntry(
    val id: String,
    val label: String,
    val info: LauncherActivityInfo? = null,
    val category: AppCategory = appCategoryOf(info?.applicationInfo),
)

fun appId(packageName: String, activity: String, userSerial: Long): String =
    "$packageName/$activity#$userSerial"

/** Launchable apps of the current profile, split into visible and hidden by the user's choices in [choices]. */
class AppRepository(private val context: Context, private val scope: CoroutineScope, private val choices: AppChoiceDao) {
    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val userManager = context.getSystemService(UserManager::class.java)
    private val installed = MutableStateFlow<List<AppEntry>>(emptyList())
    val lists: StateFlow<AppLists> = combine(installed, choices.all(), choices.shortcuts()) { apps, rows, shortcuts ->
        fun AppChoice.id() = appId(packageName, activity, userSerial)
        val custom = rows.filter { it.customCategory != null }.associate { it.id() to it.customCategory!! }
        val slots = rows.filter { it.favoriteSlot != null }.associate { it.id() to it.favoriteSlot!! }
        splitApps(withCustomCategories(apps, custom), rows.filter { it.hidden }.map { it.id() }.toSet()).copy(favoriteSlots = slots, shortcutSlots = (0 until PAGE_COUNT).associateWith { shortcutSlotsOnPage(shortcuts, it) })
    }
        .stateIn(scope, SharingStarted.Eagerly, AppLists(emptyList(), emptyList()))
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

    fun setHidden(app: AppEntry, hidden: Boolean) {
        val info = app.info ?: return
        scope.launch(Dispatchers.IO) {
            val serial = userManager.getSerialNumberForUser(info.user)
            choices.setHidden(info.componentName.packageName, info.componentName.className, serial, hidden)
        }
    }

    /** Skriver dockpladser (null fjerner favoritten) for flere apps i én transaktion. */
    fun setFavoriteSlots(changes: List<Pair<AppEntry, Int?>>) {
        scope.launch(Dispatchers.IO) {
            choices.setFavoriteSlots(changes.mapNotNull { (app, slot) ->
                val info = app.info ?: return@mapNotNull null
                val name = info.componentName
                AppChoice(name.packageName, name.className, userManager.getSerialNumberForUser(info.user), favoriteSlot = slot)
            })
        }
    }

    /** Skriver genvejspladser på [page] (null fjerner genvejen) i én transaktion. */
    fun setShortcutSlots(page: Int, changes: List<Pair<AppEntry, Int?>>) {
        scope.launch(Dispatchers.IO) {
            val rows = changes.mapNotNull { (app, slot) ->
                val info = app.info ?: return@mapNotNull null
                val name = info.componentName
                PageShortcut(page, name.packageName, name.className, userManager.getSerialNumberForUser(info.user), slot ?: -1)
            }
            choices.setShortcuts(rows.filter { it.slot >= 0 }, rows.filter { it.slot < 0 })
        }
    }

    fun setCategory(app: AppEntry, category: AppCategory) {
        val info = app.info ?: return
        scope.launch(Dispatchers.IO) {
            val serial = userManager.getSerialNumberForUser(info.user)
            choices.setCategory(info.componentName.packageName, info.componentName.className, serial, category.slug)
        }
    }

    fun openAppInfo(app: AppEntry) {
        val info = app.info ?: return
        scope.launch(Dispatchers.IO) { launcherApps.startAppDetailsActivity(info.componentName, info.user, null, null) }
    }

    fun uninstall(app: AppEntry) {
        val info = app.info ?: return
        val intent = Intent(Intent.ACTION_DELETE, Uri.fromParts("package", info.componentName.packageName, null))
            .putExtra(Intent.EXTRA_USER, info.user)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
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
            installed.value = list
        }
    }
}
