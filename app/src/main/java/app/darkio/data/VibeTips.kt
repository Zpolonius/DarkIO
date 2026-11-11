package app.darkio.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import java.time.LocalDate

private val VIBE_TIP_DATE = stringPreferencesKey("vibe_tip_date")
private val VIBE_TIP_INDEX = stringPreferencesKey("vibe_tip_index")
private val VIBE_TIP_USED = stringPreferencesKey("vibe_tip_used")

/**
 * Gets or chooses the daily vibe tip based on LocalDate.
 * Same tip is returned for multiple calls on the same LocalDate.
 * When date advances to next day, a new tip is chosen from the remaining unused pool.
 * No tip repeats until all tips in the pool have been used. When exhausted, used pool resets.
 */
suspend fun getOrChooseDailyTip(
    dataStore: DataStore<Preferences>,
    tips: List<String>,
    today: LocalDate = LocalDate.now()
): Int {
    if (tips.isEmpty()) return 0
    val todayStr = today.toString()

    var chosenIndex = 0
    dataStore.edit { prefs ->
        val savedDate = prefs[VIBE_TIP_DATE]
        val savedIndexStr = prefs[VIBE_TIP_INDEX]
        val savedUsedStr = prefs[VIBE_TIP_USED]

        val usedSet = savedUsedStr?.split(",")?.mapNotNull { it.toIntOrNull() }?.toMutableSet() ?: mutableSetOf()

        if (savedDate == todayStr && savedIndexStr != null) {
            chosenIndex = savedIndexStr.toIntOrNull() ?: 0
            if (chosenIndex !in tips.indices) chosenIndex = 0
        } else {
            val allIndices = tips.indices.toList()
            var available = allIndices.filter { it !in usedSet }
            if (available.isEmpty()) {
                usedSet.clear()
                available = allIndices
            }
            chosenIndex = available.random()
            usedSet.add(chosenIndex)

            prefs[VIBE_TIP_DATE] = todayStr
            prefs[VIBE_TIP_INDEX] = chosenIndex.toString()
            prefs[VIBE_TIP_USED] = usedSet.joinToString(",")
        }
    }
    return chosenIndex
}
