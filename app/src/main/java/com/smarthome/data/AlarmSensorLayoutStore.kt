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

private val Context.alarmSensorLayoutDataStore: DataStore<Preferences> by preferencesDataStore(name = "alarm_sensor_layout_prefs")

// AlarmSensorLayoutStore persists the freeform alarm-sensor dashboard tile
// layout (SensorTilePosition, reused as-is - see AlarmSensorRepository's
// doc comment for why this is a separate store/DataStore file from
// SensorLayoutStore rather than sharing one). Same DataStore-plus-Gson-blob
// pattern as ScheduleConfigStore/SensorLayoutStore.
class AlarmSensorLayoutStore(private val context: Context) {
    private val gson = Gson()
    private val listType = object : TypeToken<List<SensorTilePosition>>() {}.type

    companion object {
        val TILE_POSITIONS = stringPreferencesKey("alarm_sensor_tile_positions")
    }

    val positions: Flow<List<SensorTilePosition>> = context.alarmSensorLayoutDataStore.data
        .map { prefs -> decode(prefs[TILE_POSITIONS]) }

    private fun decode(json: String?): List<SensorTilePosition> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            gson.fromJson<List<SensorTilePosition>>(json, listType) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private suspend fun mutate(transform: (List<SensorTilePosition>) -> List<SensorTilePosition>) {
        context.alarmSensorLayoutDataStore.edit { prefs ->
            val current = decode(prefs[TILE_POSITIONS])
            prefs[TILE_POSITIONS] = gson.toJson(transform(current))
        }
    }

    // Swaps two tiles' orders in one transaction - see
    // SensorLayoutStore.swap's doc comment (same rationale, separate store).
    suspend fun swap(movedId: String, movedOrder: Int, displacedId: String, displacedOrder: Int) {
        mutate { list ->
            list.filterNot { it.sensorId == movedId || it.sensorId == displacedId } +
                SensorTilePosition(movedId, movedOrder) +
                SensorTilePosition(displacedId, displacedOrder)
        }
    }

    // Drops positions for alarm sensors no longer present in the latest
    // fetch - mirrors SensorLayoutStore.prune's role for temp sensors.
    suspend fun prune(liveSensorIds: Set<String>) {
        mutate { list -> list.filter { it.sensorId in liveSensorIds } }
    }

    suspend fun clear() {
        context.alarmSensorLayoutDataStore.edit { prefs -> prefs.remove(TILE_POSITIONS) }
    }
}
