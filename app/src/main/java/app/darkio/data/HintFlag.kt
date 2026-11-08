package app.darkio.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.darkIoPrefs: DataStore<Preferences> by preferencesDataStore(name = "darkio")

private val HintSeen = booleanPreferencesKey("hint_seen")

/** Mangler nøglen (første start, eller ældre installation), er hintet ikke set endnu. */
fun DataStore<Preferences>.hintSeen(): Flow<Boolean> = data.map { it[HintSeen] == true }

suspend fun DataStore<Preferences>.markHintSeen() {
    edit { it[HintSeen] = true }
}
