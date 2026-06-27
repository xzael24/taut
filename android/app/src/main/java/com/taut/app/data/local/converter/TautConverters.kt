package com.taut.app.data.local.converter

import androidx.room.TypeConverter
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Room type converters for non-primitive types.
 * Handles: List<String> ↔ JSON array string, Map<String, Long> ↔ JSON object string
 */
class TautConverters {
    @TypeConverter
    fun listToJson(list: List<String>?): String? = list?.let { Json.encodeToString(it) }

    @TypeConverter
    fun jsonToList(json: String?): List<String>? = json?.let { Json.decodeFromString(it) }

    @TypeConverter
    fun mapToJson(map: Map<String, Long>?): String? = map?.let { Json.encodeToString(it) }

    @TypeConverter
    fun jsonToMap(json: String?): Map<String, Long>? = json?.let { Json.decodeFromString(it) }
}
