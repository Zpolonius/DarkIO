package app.darkio

import android.service.notification.NotificationListenerService

/** F25: tom tjeneste. Notifikationsadgang er Androids vej til at læse aktive medie-sessioner; DarkIO læser aldrig notifikationer. */
class MediaListener : NotificationListenerService()
