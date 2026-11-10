package app.darkio.data

import android.app.StatusBarManager
import android.content.Context

/** F11: åbner notifikationspanelet. Metoden er skjult API, så den kaldes via refleksion og må aldrig crashe. */
fun expandNotificationPanel(context: Context) {
    try {
        val service = context.getSystemService(StatusBarManager::class.java) ?: return
        StatusBarManager::class.java.getMethod("expandNotificationsPanel").invoke(service)
    } catch (_: Exception) {
    }
}
