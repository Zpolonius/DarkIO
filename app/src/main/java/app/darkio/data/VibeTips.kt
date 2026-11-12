package app.darkio.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import java.time.LocalDate

private val VIBE_TIP_DATE = stringPreferencesKey("vibe_tip_date")
private val VIBE_TIP_INDEX = stringPreferencesKey("vibe_tip_index")
private val VIBE_TIP_USED = stringPreferencesKey("vibe_tip_used")
private val VIBE_TIP_HISTORY = stringPreferencesKey("vibe_tip_history")

data class VibeTipsData(
    val todayTip: String,
    val recentTips: List<String>,
)

/**
 * Gets or chooses the daily vibe tip and up to two recent tips based on LocalDate.
 */
suspend fun getDailyAndRecentTips(
    dataStore: DataStore<Preferences>,
    tips: List<String>,
    today: LocalDate = LocalDate.now(),
): VibeTipsData {
    if (tips.isEmpty()) return VibeTipsData("", emptyList())
    val todayStr = today.toString()

    var chosenIndex = 0
    var recentIndices = listOf<Int>()

    dataStore.edit { prefs ->
        val savedDate = prefs[VIBE_TIP_DATE]
        val savedIndexStr = prefs[VIBE_TIP_INDEX]
        val savedUsedStr = prefs[VIBE_TIP_USED]
        val historyStr = prefs[VIBE_TIP_HISTORY]

        val usedSet = savedUsedStr?.split(",")?.mapNotNull { it.toIntOrNull() }?.toMutableSet() ?: mutableSetOf()
        val history = historyStr?.split(",")?.mapNotNull { it.toIntOrNull() }?.toMutableList() ?: mutableListOf()

        if (savedDate == todayStr && savedIndexStr != null) {
            chosenIndex = savedIndexStr.toIntOrNull() ?: 0
            if (chosenIndex !in tips.indices) chosenIndex = 0
            recentIndices = history.filter { it in tips.indices && it != chosenIndex }.take(2)
        } else {
            val allIndices = tips.indices.toList()
            var available = allIndices.filter { it !in usedSet }
            if (available.isEmpty()) {
                usedSet.clear()
                available = allIndices
            }
            chosenIndex = available.random()
            usedSet.add(chosenIndex)

            val oldIdx = savedIndexStr?.toIntOrNull()
            if (oldIdx != null && oldIdx in tips.indices && oldIdx != chosenIndex) {
                history.remove(oldIdx)
                history.add(0, oldIdx)
            }
            recentIndices = history.filter { it in tips.indices && it != chosenIndex }.take(2)

            prefs[VIBE_TIP_DATE] = todayStr
            prefs[VIBE_TIP_INDEX] = chosenIndex.toString()
            prefs[VIBE_TIP_USED] = usedSet.joinToString(",")
            prefs[VIBE_TIP_HISTORY] = recentIndices.joinToString(",")
        }
    }

    // Ved manglende historik (fx ny installation) leveres to forskellige tips fra puljen så skærmen altid har 2 nylige tips.
    val recent = recentIndices.mapNotNull { tips.getOrNull(it) }.toMutableList()
    var offset = 1
    while (recent.size < 2 && offset < tips.size) {
        val fallbackIndex = (chosenIndex - offset + tips.size) % tips.size
        val fallbackTip = tips[fallbackIndex]
        if (fallbackIndex != chosenIndex && fallbackTip !in recent) {
            recent.add(fallbackTip)
        }
        offset++
    }

    return VibeTipsData(
        todayTip = tips.getOrElse(chosenIndex) { tips.first() },
        recentTips = recent,
    )
}

/**
 * Gets or chooses the daily vibe tip based on LocalDate.
 * Same tip is returned for multiple calls on the same LocalDate.
 * When date advances to next day, a new tip is chosen from the remaining unused pool.
 * No tip repeats until all tips in the pool have been used. When exhausted, used pool resets.
 */
suspend fun getOrChooseDailyTip(
    dataStore: DataStore<Preferences>,
    tips: List<String>,
    today: LocalDate = LocalDate.now(),
): Int {
    if (tips.isEmpty()) return 0
    val todayStr = today.toString()

    var chosenIndex = 0
    dataStore.edit { prefs ->
        val savedDate = prefs[VIBE_TIP_DATE]
        val savedIndexStr = prefs[VIBE_TIP_INDEX]
        val savedUsedStr = prefs[VIBE_TIP_USED]
        val historyStr = prefs[VIBE_TIP_HISTORY]

        val usedSet = savedUsedStr?.split(",")?.mapNotNull { it.toIntOrNull() }?.toMutableSet() ?: mutableSetOf()
        val history = historyStr?.split(",")?.mapNotNull { it.toIntOrNull() }?.toMutableList() ?: mutableListOf()

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

            val oldIdx = savedIndexStr?.toIntOrNull()
            if (oldIdx != null && oldIdx in tips.indices && oldIdx != chosenIndex) {
                history.remove(oldIdx)
                history.add(0, oldIdx)
            }
            val recentIndices = history.filter { it in tips.indices && it != chosenIndex }.take(2)

            prefs[VIBE_TIP_DATE] = todayStr
            prefs[VIBE_TIP_INDEX] = chosenIndex.toString()
            prefs[VIBE_TIP_USED] = usedSet.joinToString(",")
            prefs[VIBE_TIP_HISTORY] = recentIndices.joinToString(",")
        }
    }
    return chosenIndex
}
