package com.smarthome.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

private val Context.scheduleDataStore: DataStore<Preferences> by preferencesDataStore(name = "schedule_config_prefs")

// ScheduleConfigStore persists LocalScheduleConfig the same way
// AuthPreferences persists auth state: DataStore + Gson-serialized JSON in
// a single preference key (the whole list is small - a handful of
// schedules at most - so there's no need for per-entry keys). See
// LocalScheduleConfig's doc comment for why this exists at all: it's what
// survives a schedule being disabled (deleted server-side) so re-enabling
// doesn't need the device/hours re-entered.
class ScheduleConfigStore(private val context: Context) {
    private val gson = Gson()
    private val listType = object : TypeToken<List<LocalScheduleConfig>>() {}.type

    companion object {
        val SCHEDULE_CONFIGS = stringPreferencesKey("schedule_configs")
    }

    val configs: Flow<List<LocalScheduleConfig>> = context.scheduleDataStore.data
        .map { prefs -> decode(prefs[SCHEDULE_CONFIGS]) }

    private fun decode(json: String?): List<LocalScheduleConfig> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            gson.fromJson<List<LocalScheduleConfig>>(json, listType) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    // All mutations go through DataStore's edit{}, which is transactional
    // (read-modify-write under its own lock) - this is what keeps two
    // near-simultaneous calls (e.g. a rapid double-tap on the enable
    // switch) from one clobbering the other's write, the same failure mode
    // a plain "read Flow, mutate, save" pattern would have.
    private suspend fun mutate(transform: (List<LocalScheduleConfig>) -> List<LocalScheduleConfig>) {
        context.scheduleDataStore.edit { prefs ->
            val current = decode(prefs[SCHEDULE_CONFIGS])
            prefs[SCHEDULE_CONFIGS] = gson.toJson(transform(current))
        }
    }

    suspend fun add(device: String, fromHour: Int, fromMinute: Int, toHour: Int, toMinute: Int): LocalScheduleConfig {
        val config = LocalScheduleConfig(
            localId = UUID.randomUUID().toString(),
            device = device, fromHour = fromHour, fromMinute = fromMinute, toHour = toHour, toMinute = toMinute,
            enabled = false, remoteId = null
        )
        mutate { it + config }
        return config
    }

    // adopt records a schedule that already exists server-side (remoteId)
    // but that this local store has never heard of - e.g. one seeded from
    // the Pi's own topology.json rather than created through this app, or
    // created by a different Android install. Without this, such a
    // schedule stays invisible in SchedulesScreen (which reads only local
    // configs) even though it's live and can cause "overlaps" the user has
    // no way to see or resolve. Enabled by construction, since it's live.
    suspend fun adopt(remoteId: String, device: String, fromHour: Int, fromMinute: Int, toHour: Int, toMinute: Int): LocalScheduleConfig {
        val config = LocalScheduleConfig(
            localId = UUID.randomUUID().toString(),
            device = device, fromHour = fromHour, fromMinute = fromMinute, toHour = toHour, toMinute = toMinute,
            enabled = true, remoteId = remoteId
        )
        mutate { it + config }
        return config
    }

    suspend fun remove(localId: String) {
        mutate { list -> list.filterNot { it.localId == localId } }
    }

    suspend fun update(localId: String, transform: (LocalScheduleConfig) -> LocalScheduleConfig) {
        mutate { list -> list.map { if (it.localId == localId) transform(it) else it } }
    }
}
