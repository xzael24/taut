package com.taut.routes

import com.taut.service.CatalogService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.*

/**
 * Catalog routes — waste categories and price references.
 */
fun Route.catalogRoutes() {

    // GET /v1/categories — List all active waste categories
    get("/categories") {
        val result = CatalogService.listCategories()
        call.respond(HttpStatusCode.OK, result.toSerializableJsonObject())
    }

    // GET /v1/prices — Get current price catalog
    get("/prices") {
        val regionId = call.request.queryParameters["region_id"]?.toIntOrNull()
        val lastVersion = call.request.queryParameters["last_version"]?.toIntOrNull()

        val result = CatalogService.getPriceCatalog(regionId, lastVersion)
        call.respond(HttpStatusCode.OK, result.toSerializableJsonObject())
    }

    // GET /v1/categories/{id}/prices/history — Price history for a category
    get("/categories/{id}/prices/history") {
        val categoryId = call.parameters["id"] ?: run {
            call.respond(HttpStatusCode.BadRequest, JsonObject(mapOf("error" to JsonPrimitive("MISSING_PARAM"), "message" to JsonPrimitive("Category ID diperlukan."))))
            return@get
        }
        val regionId = call.request.queryParameters["region_id"]?.toIntOrNull()
        val fromDate = call.request.queryParameters["from_date"]
        val toDate = call.request.queryParameters["to_date"]
        val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1
        val pageSize = call.request.queryParameters["page_size"]?.toIntOrNull() ?: 20

        val result = CatalogService.getPriceHistory(categoryId, regionId, fromDate, toDate, page, pageSize)
        call.respond(HttpStatusCode.OK, result.toSerializableJsonObject())
    }
}
