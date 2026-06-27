package com.taut.routes

import kotlinx.serialization.json.*

/**
 * Converts any value into a JsonElement for serialization.
 * This enables Map<String, Any?> responses to be serialized via kotlinx.serialization.
 */
fun Any?.toJsonElement(): JsonElement = when (this) {
    null -> JsonNull
    is Boolean -> JsonPrimitive(this)
    is Number -> JsonPrimitive(this.toDouble())
    is String -> JsonPrimitive(this)
    is Map<*, *> -> @Suppress("UNCHECKED_CAST")
        JsonObject(this.entries.associate { (k, v) -> (k as String) to (v as Any?).toJsonElement() })
    is List<*> -> JsonArray(this.map { it.toJsonElement() })
    is Iterable<*> -> JsonArray(this.map { it.toJsonElement() })
    is Array<*> -> JsonArray(this.map { it.toJsonElement() })
    else -> JsonPrimitive(this.toString())
}

/**
 * Converts a JsonObject into a MutableMap<String, Any?> for route handler processing.
 */
fun JsonObject.toMutableMap(): MutableMap<String, Any?> {
    return this.entries.associate { (k, v) -> k to v.fromJsonElement() }.toMutableMap()
}

/**
 * Converts a JsonElement back into a plain Kotlin value.
 */
fun JsonElement.fromJsonElement(): Any? = when (this) {
    is JsonNull -> null
    is JsonPrimitive -> this.content
    is JsonArray -> this.map { it.fromJsonElement() }
    is JsonObject -> this.entries.associate { (k, v) -> k to v.fromJsonElement() }
}

/**
 * Converts a Map<String, Any?> to a serializable JsonObject.
 */
fun Map<String, Any?>.toSerializableJsonObject(): JsonObject {
    return JsonObject(this.entries.associate { (k, v) -> k to v.toJsonElement() })
}
