package com.taut.service

import com.taut.db.Jdbc
import java.time.*
import java.util.*

/**
 * DashboardService — aggregated analytics for operator and DLH dashboards.
 * Uses raw JDBC SQL.
 */
object DashboardService {

    private val clock = Clock.systemUTC()
    private val jakarta = ZoneId.of("Asia/Jakarta")

    fun getOperatorDashboard(
        bankSampahId: String,
        period: String,
        fromDate: String? = null,
        toDate: String? = null
    ): Map<String, Any> {
        val bankId = try { UUID.fromString(bankSampahId) } catch (_: Exception) {
            return mapOf("error" to "INVALID_ID", "message" to "Format bank ID tidak valid.")
        }
        val now = Instant.now(clock)
        val (startLdt, endLdt) = resolveDateRange(period, fromDate, toDate, now)

        // Aggregate metrics
        val metrics = Jdbc.withConn { conn ->
            Jdbc.querySingle(conn, """
                SELECT COALESCE(SUM(total_weight), 0) as total_weight,
                       COALESCE(SUM(total_value), 0) as total_value,
                       COUNT(*) as tx_count
                FROM transactions
                WHERE bank_sampah_id = ? AND status IN ('CONFIRMED', 'SYNCED')
                  AND created_at >= ? AND created_at <= ?
            """.trimIndent(), listOf(bankId, startLdt, endLdt))
        }

        val totalWeight = (metrics?.get("total_weight") as? Number)?.toLong() ?: 0L
        val totalValue = (metrics?.get("total_value") as? Number)?.toLong() ?: 0L
        val txCount = (metrics?.get("tx_count") as? Number)?.toLong() ?: 0L

        val uniqueCustomers = Jdbc.withConn { conn ->
            Jdbc.count(conn, """
                SELECT COUNT(DISTINCT customer_id) FROM transactions
                WHERE bank_sampah_id = ? AND status IN ('CONFIRMED', 'SYNCED')
                  AND created_at >= ? AND created_at <= ?
            """.trimIndent(), listOf(bankId, startLdt, endLdt))
        }

        // Category breakdown
        val catBreakdown = Jdbc.withConn { conn ->
            Jdbc.queryMap(conn, """
                SELECT wc.category_group, COALESCE(SUM(ti.total_value), 0) as cat_value
                FROM transaction_items ti
                JOIN transactions t ON ti.transaction_id = t.id
                JOIN waste_categories wc ON ti.category_id = wc.id
                WHERE t.bank_sampah_id = ? AND t.status IN ('CONFIRMED', 'SYNCED')
                  AND t.created_at >= ? AND t.created_at <= ?
                GROUP BY wc.category_group
                ORDER BY cat_value DESC
            """.trimIndent(), listOf(bankId, startLdt, endLdt))
        }.map { row ->
            val group = row["category_group"] ?: ""
            val value = (row["cat_value"] as? Number)?.toLong() ?: 0L
            val pct = if (totalValue > 0) (value.toDouble() / totalValue * 100.0) else 0.0
            mapOf("category" to group, "total_value" to value, "percentage" to String.format("%.1f", pct))
        }

        // Daily trend (last 30 days)
        val thirtyDaysAgo = nowLdt(now.minusSeconds(30L * 24 * 3600))
        val dailyTrend = Jdbc.withConn { conn ->
            Jdbc.queryMap(conn, """
                SELECT DATE(created_at) as tx_date,
                       COALESCE(SUM(total_weight), 0) as daily_weight,
                       COALESCE(SUM(total_value), 0) as daily_value,
                       COUNT(*) as daily_count
                FROM transactions
                WHERE bank_sampah_id = ? AND status IN ('CONFIRMED', 'SYNCED')
                  AND created_at >= ?
                GROUP BY DATE(created_at)
                ORDER BY tx_date DESC
                LIMIT 30
            """.trimIndent(), listOf(bankId, thirtyDaysAgo))
        }.map { row ->
            mapOf(
                "date" to (row["tx_date"]?.toString() ?: ""),
                "total_weight" to ((row["daily_weight"] as? Number)?.toLong() ?: 0L),
                "total_value" to ((row["daily_value"] as? Number)?.toLong() ?: 0L),
                "transaction_count" to ((row["daily_count"] as? Number)?.toLong() ?: 0L)
            )
        }

        return mapOf(
            "total_weight_grams" to totalWeight,
            "total_value_satuan" to totalValue,
            "transaction_count" to txCount,
            "unique_customers" to uniqueCustomers,
            "category_breakdown" to catBreakdown,
            "daily_trend" to dailyTrend
        )
    }

    fun getKecamatanDashboard(kotaId: Int, fromDate: String, toDate: String): Map<String, Any> {
        val fromInstant = try { Instant.parse(fromDate) } catch (_: Exception) {
            return mapOf("error" to "INVALID_DATE", "message" to "Format from_date tidak valid.")
        }
        val toInstant = try { Instant.parse(toDate) } catch (_: Exception) {
            return mapOf("error" to "INVALID_DATE", "message" to "Format to_date tidak valid.")
        }
        val fromLdt = fromInstant.atOffset(ZoneOffset.UTC).toLocalDateTime()
        val toLdt = toInstant.atOffset(ZoneOffset.UTC).toLocalDateTime()

        val metrics = Jdbc.withConn { conn ->
            Jdbc.querySingle(conn, """
                SELECT COALESCE(SUM(total_weight), 0) as total_weight,
                       COALESCE(SUM(total_value), 0) as total_value,
                       COUNT(*) as total_tx,
                       COUNT(DISTINCT bank_sampah_id) as active_banks,
                       COUNT(DISTINCT customer_id) as active_customers
                FROM transactions
                WHERE status IN ('CONFIRMED', 'SYNCED')
                  AND created_at >= ? AND created_at <= ?
            """.trimIndent(), listOf(fromLdt, toLdt))
        }

        // Per-kecamatan breakdown via villages join
        val kecamatanBreakdown = Jdbc.withConn { conn ->
            Jdbc.queryMap(conn, """
                SELECT k.id as kecamatan_id, k.name_id as kecamatan_name,
                       COALESCE(SUM(t.total_weight), 0) as total_weight,
                       COALESCE(SUM(t.total_value), 0) as total_value,
                       COUNT(DISTINCT t.id) as transaction_count,
                       COUNT(DISTINCT t.bank_sampah_id) as active_banks,
                       COUNT(DISTINCT t.customer_id) as active_customers
                FROM transactions t
                JOIN waste_banks wb ON wb.id = t.bank_sampah_id
                JOIN villages v ON v.id = wb.village_id
                JOIN kecamatans k ON k.id = v.kecamatan_id
                WHERE t.status IN ('CONFIRMED', 'SYNCED')
                  AND k.kota_id = ?
                  AND t.created_at >= ? AND t.created_at <= ?
                GROUP BY k.id, k.name_id
                ORDER BY total_value DESC
            """.trimIndent(), listOf(kotaId, fromLdt, toLdt))
        }.map { row ->
            mapOf(
                "kecamatan_id" to ((row["kecamatan_id"] as? Number)?.toInt() ?: 0),
                "kecamatan_name" to (row["kecamatan_name"] ?: ""),
                "total_weight_grams" to ((row["total_weight"] as? Number)?.toLong() ?: 0L),
                "total_value_satuan" to ((row["total_value"] as? Number)?.toLong() ?: 0L),
                "transaction_count" to ((row["transaction_count"] as? Number)?.toLong() ?: 0L),
                "active_banks" to ((row["active_banks"] as? Number)?.toLong() ?: 0L),
                "active_customers" to ((row["active_customers"] as? Number)?.toLong() ?: 0L)
            )
        }

        return mapOf(
            "kecamatans" to kecamatanBreakdown,
            "totals" to mapOf(
                "total_weight_grams" to ((metrics?.get("total_weight") as? Number)?.toLong() ?: 0L),
                "total_value_satuan" to ((metrics?.get("total_value") as? Number)?.toLong() ?: 0L),
                "total_transactions" to ((metrics?.get("total_tx") as? Number)?.toLong() ?: 0L),
                "active_banks" to ((metrics?.get("active_banks") as? Number)?.toLong() ?: 0L),
                "active_customers" to ((metrics?.get("active_customers") as? Number)?.toLong() ?: 0L)
            )
        )
    }

    fun generateMonthlyReport(bankSampahId: String, year: Int, month: Int, cdnBaseUrl: String = "https://cdn.taut.id"): Map<String, Any> {
        require(year in 2020..2099) { "Year out of range" }
        require(month in 1..12) { "Month out of range" }
        require(bankSampahId.isNotBlank()) { "Bank ID must not be blank" }
        return mapOf(
            "report_url" to "$cdnBaseUrl/reports/$bankSampahId/$year/${String.format("%02d", month)}/report.pdf",
            "generated_at" to Instant.now(clock).toString(),
            "message" to "Laporan sedang diproses. URL akan tersedia dalam beberapa menit."
        )
    }

    private fun resolveDateRange(period: String, fromDate: String?, toDate: String?, now: Instant): Pair<LocalDateTime, LocalDateTime> {
        if (period == "custom" && fromDate != null && toDate != null) {
            val from = try { Instant.parse(fromDate) } catch (_: Exception) { now.minusSeconds(86400) }
            val to = try { Instant.parse(toDate) } catch (_: Exception) { now }
            return Pair(from.atOffset(ZoneOffset.UTC).toLocalDateTime(), to.atOffset(ZoneOffset.UTC).toLocalDateTime())
        }
        val localDate = now.atZone(jakarta).toLocalDate()
        val (startDate, endDate) = when (period.lowercase()) {
            "today" -> Pair(localDate.atStartOfDay(jakarta), localDate.plusDays(1).atStartOfDay(jakarta))
            "this_week" -> Pair(localDate.with(java.time.DayOfWeek.MONDAY).atStartOfDay(jakarta), localDate.plusDays(1).atStartOfDay(jakarta))
            "this_month" -> Pair(localDate.withDayOfMonth(1).atStartOfDay(jakarta), localDate.plusDays(1).atStartOfDay(jakarta))
            else -> Pair(now.minusSeconds(30 * 86400).atZone(jakarta), now.plusSeconds(86400).atZone(jakarta))
        }
        return Pair(
            startDate.toInstant().atOffset(ZoneOffset.UTC).toLocalDateTime(),
            endDate.toInstant().atOffset(ZoneOffset.UTC).toLocalDateTime()
        )
    }

    private fun nowLdt(instant: Instant): LocalDateTime = instant.atOffset(ZoneOffset.UTC).toLocalDateTime()
}
