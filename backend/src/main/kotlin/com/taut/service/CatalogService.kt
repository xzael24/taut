package com.taut.service

import com.taut.db.Jdbc
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.*

/**
 * CatalogService — waste categories and price reference management.
 * Uses raw JDBC SQL via Jdbc helper.
 */
object CatalogService {

    fun listCategories(): Map<String, Any> {
        val rows = Jdbc.withConn { conn ->
            Jdbc.queryMap(conn,
                "SELECT * FROM waste_categories WHERE is_active = true ORDER BY sort_order ASC")
        }
        val categories = rows.map { rowToCategory(it) }

        val versionRow = Jdbc.withConn { conn ->
            Jdbc.querySingle(conn, "SELECT version FROM catalog_version LIMIT 1")
        }
        val version = (versionRow?.get("version") as? Number)?.toInt() ?: 1

        return mapOf("data" to categories, "version" to version)
    }

    fun getPriceCatalog(regionId: Int? = null, lastVersion: Int? = null): Map<String, Any> {
        val sql = buildString {
            append("SELECT * FROM price_references WHERE is_current = true")
            if (regionId != null) {
                append(" AND (region_id = ? OR region_id = 0)")
            } else {
                append(" AND (region_id = 0 OR region_type = 'national')")
            }
            if (lastVersion != null) {
                append(" AND version > ?")
            }
            append(" ORDER BY category_id ASC")
        }
        val params = mutableListOf<Any?>()
        if (regionId != null) params.add(regionId)
        if (lastVersion != null) params.add(lastVersion)

        val rows = Jdbc.withConn { conn -> Jdbc.queryMap(conn, sql, params) }
        val prices = rows.map { rowToPrice(it) }

        val currentVersion = Jdbc.withConn { conn ->
            Jdbc.querySingle(conn, "SELECT version FROM catalog_version LIMIT 1")
        }?.get("version")?.let { (it as? Number)?.toInt() } ?: 1

        return mapOf("data" to prices, "version" to currentVersion)
    }

    fun getPriceHistory(
        categoryId: String,
        regionId: Int? = null,
        fromDate: String? = null,
        toDate: String? = null,
        page: Int = 1,
        pageSize: Int = 20
    ): Map<String, Any?> {
        val catId = try { UUID.fromString(categoryId) } catch (_: Exception) { return mapOf("data" to emptyList<Any>()) }
        val offset = ((page - 1) * pageSize).toLong()

        val conditions = mutableListOf("category_id = ?")
        val params = mutableListOf<Any?>(catId)

        if (regionId != null) { conditions.add("region_id = ?"); params.add(regionId) }
        if (fromDate != null) {
            val f = try { Instant.parse(fromDate).atOffset(ZoneOffset.UTC).toLocalDateTime() } catch (_: Exception) { null }
            if (f != null) { conditions.add("effective_from >= ?"); params.add(f) }
        }
        if (toDate != null) {
            val t = try { Instant.parse(toDate).atOffset(ZoneOffset.UTC).toLocalDateTime() } catch (_: Exception) { null }
            if (t != null) { conditions.add("effective_from <= ?"); params.add(t) }
        }

        val where = conditions.joinToString(" AND ")
        val countSql = "SELECT COUNT(*) as cnt FROM price_references WHERE $where"
        val totalCount = Jdbc.withConn { conn -> Jdbc.count(conn, countSql, params) }

        val sql = "SELECT * FROM price_references WHERE $where ORDER BY effective_from DESC LIMIT $pageSize OFFSET $offset"
        val rows = Jdbc.withConn { conn -> Jdbc.queryMap(conn, sql, params) }
        val prices = rows.map { rowToPrice(it) }

        return mapOf(
            "data" to prices,
            "next_cursor" to if (offset + prices.size < totalCount) (page + 1).toString() else null,
            "total_count" to totalCount,
            "page" to page,
            "page_size" to pageSize
        )
    }

    private fun rowToCategory(r: Map<String, Any?>) = mapOf(
        "id" to (r["id"] as UUID).toString(),
        "code" to (r["code"] ?: ""),
        "name_id" to (r["name_id"] ?: ""),
        "name_en" to (r["name_en"]),
        "category_group" to (r["category_group"] ?: ""),
        "unit_type" to (r["unit_type"] ?: "kg"),
        "unit_price" to ((r["unit_price"] as? Number)?.toLong() ?: 0L),
        "photo_url" to r["photo_url"],
        "sort_order" to ((r["sort_order"] as? Number)?.toInt() ?: 0),
        "created_at" to (r["created_at"]?.toString() ?: ""),
        "updated_at" to (r["updated_at"]?.toString() ?: "")
    )

    private fun rowToPrice(r: Map<String, Any?>) = mapOf(
        "id" to (r["id"] as UUID).toString(),
        "category_id" to (r["category_id"] as UUID).toString(),
        "region_id" to ((r["region_id"] as? Number)?.toInt() ?: 0),
        "region_type" to (r["region_type"] ?: "national"),
        "source" to (r["price_source"] ?: ""),
        "price_per_unit" to ((r["price_per_unit"] as? Number)?.toLong() ?: 0L),
        "effective_from" to (r["effective_from"]?.toString() ?: ""),
        "effective_to" to r["effective_to"]?.toString(),
        "version" to ((r["version"] as? Number)?.toInt() ?: 1),
        "is_current" to (r["is_current"] ?: true)
    )
}
