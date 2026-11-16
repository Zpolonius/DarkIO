package app.darkio.data

import android.app.AutomaticZenRule
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.service.notification.Condition

const val FYRAFTEN_RULE_NAME = "Fyraften"
const val FYRAFTEN_CONDITION_URI_STRING = "darkio://zen/fyraften"

/**
 * Ren beslutningsfunktion for fyraften AutomaticZenRule:
 * - Returnerer `true` når Fyraften trykkes (aktiverer reglen).
 * - Returnerer `false` når arbejdstid starter (deaktiverer reglen).
 * - Returnerer `null` når ingen ændring er påkrævet.
 */
fun desiredZenRuleActive(
    isWorkHours: Boolean,
    wasWorkHours: Boolean?,
    fyraftenClicked: Boolean,
): Boolean? {
    if (fyraftenClicked) return true
    if (isWorkHours && wasWorkHours != true) return false
    return null
}

/**
 * Finder eksisterende eller opretter ny "Fyraften" AutomaticZenRule.
 * Tjekker altid [NotificationManager.isNotificationPolicyAccessGranted] først.
 * Returnerer regel-id eller null hvis tilladelse mangler eller oprettelse fejler.
 */
fun ensureFyraftenZenRule(context: Context, existingId: String?): String? {
    val nm = context.getSystemService(NotificationManager::class.java) ?: return null
    if (!nm.isNotificationPolicyAccessGranted) return null

    if (existingId != null) {
        try {
            val rule = nm.getAutomaticZenRule(existingId)
            if (rule != null && rule.name == FYRAFTEN_RULE_NAME) {
                return existingId
            }
        } catch (_: Throwable) {}
    }

    try {
        val existingEntry = nm.automaticZenRules.entries.firstOrNull { it.value.name == FYRAFTEN_RULE_NAME }
        if (existingEntry != null) {
            return existingEntry.key
        }
    } catch (_: Throwable) {}

    return try {
        val rule = AutomaticZenRule(
            FYRAFTEN_RULE_NAME,
            null,
            null,
            Uri.parse(FYRAFTEN_CONDITION_URI_STRING),
            null,
            NotificationManager.INTERRUPTION_FILTER_PRIORITY,
            true,
        )
        nm.addAutomaticZenRule(rule)
    } catch (_: Throwable) {
        null
    }
}

/**
 * Sætter tilstanden på Fyraften AutomaticZenRule (aktiv eller inaktiv).
 * Tjekker altid [NotificationManager.isNotificationPolicyAccessGranted] først.
 */
fun setFyraftenZenRuleActive(context: Context, ruleId: String, active: Boolean) {
    val nm = context.getSystemService(NotificationManager::class.java) ?: return
    if (!nm.isNotificationPolicyAccessGranted) return

    val state = if (active) Condition.STATE_TRUE else Condition.STATE_FALSE
    val condition = Condition(Uri.parse(FYRAFTEN_CONDITION_URI_STRING), FYRAFTEN_RULE_NAME, state)
    try {
        nm.setAutomaticZenRuleState(ruleId, condition)
    } catch (_: Throwable) {}
}

/**
 * Åbner Androids indstillinger for adgang til notifikationspolitik (Forstyr ikke-adgang).
 */
fun openNotificationPolicyAccessSettings(context: Context) {
    try {
        val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (_: Throwable) {}
}

/**
 * Åbner indstillinger for Fyraften-reglen, eller falder tilbage til notifikationspolitik.
 */
fun openFyraftenRuleSettings(context: Context, ruleId: String?) {
    try {
        val intent = if (ruleId != null) {
            Intent(Settings.ACTION_AUTOMATIC_ZEN_RULE_SETTINGS).apply {
                putExtra(Settings.EXTRA_AUTOMATIC_ZEN_RULE_ID, ruleId)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } else {
            Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        context.startActivity(intent)
    } catch (_: Throwable) {
        openNotificationPolicyAccessSettings(context)
    }
}
