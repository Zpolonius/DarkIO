package app.darkio.data

import android.app.AutomaticZenRule
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.service.notification.Condition
import androidx.core.net.toUri
import app.darkio.WorkHoursConfigActivity

const val FYRAFTEN_RULE_NAME = "Fyraften"
const val FYRAFTEN_CONDITION_URI_STRING = "darkio://zen/fyraften"

internal data class ZenRuleInfo(
    val id: String,
    val name: String?,
    val hasExpectedConfigurationActivity: Boolean,
)

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
 * Tjekker om appen har adgang til notifikationspolitik (Forstyr ikke).
 */
fun isNotificationPolicyAccessGranted(context: Context): Boolean {
    val nm = context.getSystemService(NotificationManager::class.java) ?: return false
    return nm.isNotificationPolicyAccessGranted
}

/**
 * Finder eksisterende eller opretter ny "Fyraften" AutomaticZenRule.
 * Tjekker altid [NotificationManager.isNotificationPolicyAccessGranted] først.
 * Sikrer at [AutomaticZenRule.getConfigurationActivity] peger på [WorkHoursConfigActivity],
 * så reglen kan oprettes og redigeres i Androids indstillinger.
 * Returnerer regel-id eller null hvis tilladelse mangler eller oprettelse fejler.
 */
fun ensureFyraftenZenRule(context: Context, existingId: String?): String? {
    val nm = context.getSystemService(NotificationManager::class.java) ?: return null
    val configComponent = ComponentName(context, WorkHoursConfigActivity::class.java)
    return ensureFyraftenZenRuleWith(
        existingId = existingId,
        hasPolicyAccess = { nm.isNotificationPolicyAccessGranted },
        getRule = { id ->
            nm.getAutomaticZenRule(id)?.let { rule ->
                ZenRuleInfo(
                    id = id,
                    name = rule.name,
                    hasExpectedConfigurationActivity = rule.configurationActivity == configComponent,
                )
            }
        },
        listRules = {
            nm.automaticZenRules.entries.map { (id, rule) ->
                ZenRuleInfo(
                    id = id,
                    name = rule.name,
                    hasExpectedConfigurationActivity = rule.configurationActivity == configComponent,
                )
            }
        },
        updateRuleConfigurationActivity = { id ->
            val existing = nm.getAutomaticZenRule(id) ?: return@ensureFyraftenZenRuleWith false
            existing.configurationActivity = configComponent
            nm.updateAutomaticZenRule(id, existing)
        },
        createRuleWithConfigurationActivity = {
            val rule = AutomaticZenRule.Builder(
                FYRAFTEN_RULE_NAME,
                FYRAFTEN_CONDITION_URI_STRING.toUri(),
            )
                .setConfigurationActivity(configComponent)
                .setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
                .setEnabled(true)
                .setManualInvocationAllowed(true)
                .build()
            nm.addAutomaticZenRule(rule)
        },
    )
}

internal fun ensureFyraftenZenRuleWith(
    existingId: String?,
    hasPolicyAccess: () -> Boolean,
    getRule: (String) -> ZenRuleInfo?,
    listRules: () -> List<ZenRuleInfo>,
    updateRuleConfigurationActivity: (String) -> Boolean,
    createRuleWithConfigurationActivity: () -> String?,
): String? {
    if (!hasPolicyAccess()) return null

    var failedUpdateId: String? = null
    if (existingId != null) {
        val existing = runCatching { getRule(existingId) }.getOrNull()
        if (existing != null && existing.name == FYRAFTEN_RULE_NAME) {
            if (existing.hasExpectedConfigurationActivity) return existingId
            if (runCatching { updateRuleConfigurationActivity(existingId) }.getOrDefault(false)) {
                return existingId
            }
            failedUpdateId = existingId
        }
    }

    val candidate = runCatching { listRules() }.getOrNull()
        ?.firstOrNull { it.name == FYRAFTEN_RULE_NAME && it.id != failedUpdateId }
    if (candidate != null) {
        if (candidate.hasExpectedConfigurationActivity) return candidate.id
        if (runCatching { updateRuleConfigurationActivity(candidate.id) }.getOrDefault(false)) {
            return candidate.id
        }
    }

    return runCatching { createRuleWithConfigurationActivity() }.getOrNull()
}

/**
 * Sætter tilstanden på Fyraften AutomaticZenRule (aktiv eller inaktiv).
 * Tjekker altid [NotificationManager.isNotificationPolicyAccessGranted] først.
 * Returnerer `true` ved succes, `false` hvis tilladelse mangler eller kaldet fejler.
 */
fun setFyraftenZenRuleActive(context: Context, ruleId: String, active: Boolean): Boolean {
    val nm = context.getSystemService(NotificationManager::class.java) ?: return false
    return setFyraftenZenRuleActiveWith(
        ruleId = ruleId,
        active = active,
        hasPolicyAccess = { nm.isNotificationPolicyAccessGranted },
        setRuleState = { id, isActive ->
            val state = if (isActive) Condition.STATE_TRUE else Condition.STATE_FALSE
            val condition = Condition(FYRAFTEN_CONDITION_URI_STRING.toUri(), FYRAFTEN_RULE_NAME, state)
            nm.setAutomaticZenRuleState(id, condition)
        },
    )
}

internal fun setFyraftenZenRuleActiveWith(
    ruleId: String,
    active: Boolean,
    hasPolicyAccess: () -> Boolean,
    setRuleState: (String, Boolean) -> Unit,
): Boolean {
    if (!hasPolicyAccess()) return false
    return try {
        setRuleState(ruleId, active)
        true
    } catch (_: Throwable) {
        false
    }
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
