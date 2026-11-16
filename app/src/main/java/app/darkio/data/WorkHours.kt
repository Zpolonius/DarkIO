package app.darkio.data

import java.time.ZonedDateTime

val WORK_SHORTCUT_SEED_PACKAGES = listOf(
    "com.microsoft.office.outlook",
    "com.microsoft.teams",
    "com.Slack",
)

/**
 * Afgør om [now] falder inden for arbejdstiden defineret i [settings].
 * Håndterer ugedage, start-/sluttidspunkter, fyraften-override for i dag, midnat og sommertid.
 */
fun isWorkHours(settings: Settings, now: ZonedDateTime): Boolean {
    if (settings.workEndOverrideDate != null && settings.workEndOverrideDate == now.toLocalDate()) {
        return false
    }
    if (now.dayOfWeek !in settings.workDays) {
        return false
    }
    val nowMinutes = now.hour * 60 + now.minute
    return if (settings.workStartMinutes < settings.workEndMinutes) {
        nowMinutes in settings.workStartMinutes until settings.workEndMinutes
    } else if (settings.workStartMinutes > settings.workEndMinutes) {
        nowMinutes >= settings.workStartMinutes || nowMinutes < settings.workEndMinutes
    } else {
        false
    }
}

private fun appPackageName(app: AppEntry): String =
    app.info?.componentName?.packageName ?: app.id.substringBefore('/')

private fun appActivityName(app: AppEntry): String =
    app.info?.componentName?.className ?: app.id.substringAfter('/').substringBefore('#')

private fun appUserSerial(app: AppEntry): Long =
    app.id.substringAfter('#', "0").toLongOrNull() ?: 0L

/**
 * Danner start-genveje til arbejdsrækken ud fra installerede apps:
 * com.microsoft.office.outlook, com.microsoft.teams, com.Slack (i den rækkefølge, pladser 0..2).
 * Manglende apps udelades.
 */
fun workShortcutsSeed(installed: List<AppEntry>): List<PageShortcut> {
    return WORK_SHORTCUT_SEED_PACKAGES.mapNotNull { pkg ->
        installed.firstOrNull { appPackageName(it) == pkg }
    }.take(3).mapIndexed { slot, app ->
        PageShortcut(
            page = WORK_SHORTCUTS_PAGE,
            packageName = appPackageName(app),
            activity = appActivityName(app),
            userSerial = appUserSerial(app),
            slot = slot,
        )
    }
}
