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

private val Context.sensorLayoutDataStore: DataStore<Preferences> by preferencesDataStore(name = "sensor_layout_prefs")

// SensorLayoutStore persists the sensor-dashboard tile layout
// (SensorTilePosition, see its doc comment for why it's a dense integer
// order rather than x/y) the same way ScheduleConfigStore persists
// LocalScheduleConfig: DataStore + Gson-serialized JSON in a single
// preference key - there's only ever a handful of sensors, so a per-entry
// key scheme buys nothing here either.
class SensorLayoutStore(private val context: Context) {
    private val gson = Gson()
    private val listType = object : TypeToken<List<SensorTilePosition>>() {}.type

    companion object {
        val TILE_POSITIONS = stringPreferencesKey("sensor_tile_positions")
    }

    val positions: Flow<List<SensorTilePosition>> = context.sensorLayoutDataStore.data
        .map { prefs -> decode(prefs[TILE_POSITIONS]) }

    private fun decode(json: String?): List<SensorTilePosition> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            gson.fromJson<List<SensorTilePosition>>(json, listType) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    // Same transactional read-modify-write rationale as
    // ScheduleConfigStore.mutate - keeps two near-simultaneous drags (or a
    // drag racing a prune()) from clobbering each other.
    private suspend fun mutate(transform: (List<SensorTilePosition>) -> List<SensorTilePosition>) {
        context.sensorLayoutDataStore.edit { prefs ->
            val current = decode(prefs[TILE_POSITIONS])
            prefs[TILE_POSITIONS] = gson.toJson(transform(current))
        }
    }

    // Swaps two tiles' orders in one transaction, so there's never a
    // moment where the store holds two entries with the same order (which
    // reading it mid-write could otherwise observe as two tiles briefly
    // overlapping) - see SensorRepository.swapSensorTilePositions's doc
    // comment.
    suspend fun swap(movedId: String, movedOrder: Int, displacedId: String, displacedOrder: Int) {
        mutate { list ->
            list.filterNot { it.sensorId == movedId || it.sensorId == displacedId } +
                SensorTilePosition(movedId, movedOrder) +
                SensorTilePosition(displacedId, displacedOrder)
        }
    }

    // Drops positions for sensors no longer present in the latest fetch, so
    // a decommissioned/renamed sensor doesn't leave a permanent orphaned
    // entry behind. Mirrors reconcileSchedules()'s cleanup role for
    // schedules - called from fetchSensors() on every poll.
    suspend fun prune(liveSensorIds: Set<String>) {
        mutate { list -> list.filter { it.sensorId in liveSensorIds } }
    }

    // Wipes every locally-persisted position. Not scoped to the logged-in
    // serial number, so this must run on logout (see SensorRepository
    // .clearSensorTileLayout's doc comment) as well as on an explicit
    // "Reset Layout" from the user.
    suspend fun clear() {
        context.sensorLayoutDataStore.edit { prefs -> prefs.remove(TILE_POSITIONS) }
    }
}
