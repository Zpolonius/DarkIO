package app.darkio.data

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import app.darkio.R
import app.darkio.dashboard.DEFAULT_STEP_GOAL
import app.darkio.ui.theme.DarkIOColors
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class HomeCard(@StringRes val title: Int, @StringRes val sub: Int) {
    Clock(R.string.card_clock, R.string.card_clock_sub),
    Calendar(R.string.card_calendar, R.string.card_calendar_sub),
    Focus(R.string.card_focus, R.string.card_focus_sub),
    Tip(R.string.card_tip, R.string.card_tip_sub),
    Workout(R.string.card_workout, R.string.card_workout_sub),
    Steps(R.string.card_steps, R.string.card_steps_sub),
    Calories(R.string.card_calories, R.string.card_calories_sub),
}

enum class AccentChoice(val color: Color, @StringRes val label: Int) {
    Lime(DarkIOColors.AccentLime, R.string.accent_lime),
    Coral(DarkIOColors.Coral, R.string.accent_coral),
    Lilac(DarkIOColors.Lilac, R.string.accent_lilac),
    Mint(DarkIOColors.Mint, R.string.accent_mint),
    Sky(DarkIOColors.Sky, R.string.accent_sky),
}

private const val MIN_STEP_GOAL = 1_000L
private const val MAX_STEP_GOAL = 50_000L
const val STEP_GOAL_STEP = 500L

data class Settings(
    val accent: AccentChoice = AccentChoice.Lime,
    val cards: Set<HomeCard> = HomeCard.entries.toSet(),
    val stepGoal: Long = DEFAULT_STEP_GOAL,
) {
    /** Skridtmålet holdes mellem 1.000 og 50.000. */
    fun withStepGoal(goal: Long) = copy(stepGoal = goal.coerceIn(MIN_STEP_GOAL, MAX_STEP_GOAL))
}

private val AccentKey = stringPreferencesKey("accent")
private val StepGoalKey = longPreferencesKey("step_goal")
private fun cardKey(card: HomeCard) = booleanPreferencesKey("card_${card.name}")

private fun Preferences.toSettings() = Settings(
    accent = AccentChoice.entries.firstOrNull { it.name == this[AccentKey] } ?: AccentChoice.Lime,
    cards = HomeCard.entries.filter { this[cardKey(it)] != false }.toSet(),
    stepGoal = this[StepGoalKey] ?: DEFAULT_STEP_GOAL,
)

/** Mangler en nøgle (første start, eller ældre installation), gælder standarden: lime, alle kort til, 8.000 skridt. */
fun DataStore<Preferences>.settings(): Flow<Settings> = data.map { it.toSettings() }

/** Anvender [change] på de gemte værdier i selve skrivningen, så hurtige ændringer ikke overskriver hinanden. Kun ændrede nøgler skrives. */
suspend fun DataStore<Preferences>.updateSettings(change: (Settings) -> Settings) {
    edit { p ->
        val old = p.toSettings()
        val new = change(old)
        if (new.accent != old.accent) p[AccentKey] = new.accent.name
        HomeCard.entries.filter { (it in new.cards) != (it in old.cards) }.forEach { p[cardKey(it)] = it in new.cards }
        if (new.stepGoal != old.stepGoal) p[StepGoalKey] = new.stepGoal
    }
}
