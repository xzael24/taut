package com.taut.db

import com.taut.config.DataSources
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.*

/**
 * Raw JDBC helper for database operations.
 * Used instead of Exposed ORM to avoid type inference issues with Exposed 0.51.x + Kotlin 2.1.20.
 */
object Jdbc {

    fun <T> withConn(block: (Connection) -> T): T {
        return DataSources.primary.connection.use { conn ->
            block(conn)
        }
    }

    fun <T> withTransaction(block: (Connection) -> T): T {
        return DataSources.primary.connection.use { conn ->
            conn.autoCommit = false
            try {
                val result = block(conn)
                conn.commit()
                result
            } catch (e: Exception) {
                conn.rollback()
                throw e
            } finally {
                conn.autoCommit = true
            }
        }
    }

    /**
     * TODO: For nested transaction support (savepoints), add a withSavepoint function.
     * Currently, withTransaction uses connection-level transaction boundaries which do
     * NOT support nesting. If a function calls withTransaction inside another withTransaction,
     * the inner block reuses the same connection-level transaction — a rollback in the
     * inner block would roll back the entire outer transaction.
     *
     * Future enhancement: Use JDBC Savepoints to allow partial rollback:
     *   conn.setSavepoint("nested_tx")
     *   conn.rollback(savepoint)
     * See: java.sql.Connection.setSavepoint()
     */

    fun insert(conn: Connection, table: String, params: Map<String, Any?>): UUID {
        val columns = params.keys.joinToString(",")
        val placeholders = params.keys.joinToString(",") { "?" }
        val id = UUID.randomUUID()
        val allParams = params.toMutableMap()
        allParams.putIfAbsent("id", id)

        val sql = "INSERT INTO $table ($columns) VALUES ($placeholders)"
        val colOrder = allParams.keys.toList()
        val stmt = conn.prepareStatement(sql)
        try {
            setParams(stmt, colOrder.map { allParams[it] })
            stmt.executeUpdate()
        } finally {
            stmt.close()
        }
        return id
    }

    fun exec(conn: Connection, sql: String, params: List<Any?> = emptyList()): Int {
        val stmt = conn.prepareStatement(sql)
        try {
            setParams(stmt, params)
            return stmt.executeUpdate()
        } finally {
            stmt.close()
        }
    }

    fun queryMap(conn: Connection, sql: String, params: List<Any?> = emptyList()): List<Map<String, Any?>> {
        val rows = mutableListOf<Map<String, Any?>>()
        val stmt = conn.prepareStatement(sql)
        try {
            setParams(stmt, params)
            val rs = stmt.executeQuery()
            val meta = rs.metaData
            val colCount = meta.columnCount
            val colNames = (1..colCount).map { meta.getColumnLabel(it) }
            while (rs.next()) {
                val row = mutableMapOf<String, Any?>()
                for (i in 1..colCount) {
                    row[colNames[i - 1]] = getObject(rs, i)
                }
                rows.add(row)
            }
        } finally {
            stmt.close()
        }
        return rows
    }

    fun querySingle(conn: Connection, sql: String, params: List<Any?> = emptyList()): Map<String, Any?>? {
        val rows = queryMap(conn, sql, params)
        return rows.firstOrNull()
    }

    fun exists(conn: Connection, sql: String, params: List<Any?> = emptyList()): Boolean {
        return querySingle(conn, sql, params) != null
    }

    fun count(conn: Connection, sql: String, params: List<Any?> = emptyList()): Long {
        val row = querySingle(conn, sql, params)
        return (row?.values?.firstOrNull() as? Number)?.toLong() ?: 0L
    }

    private fun setParams(stmt: PreparedStatement, params: List<Any?>) {
        params.forEachIndexed { index, value ->
            val pos = index + 1
            when (value) {
                null -> stmt.setNull(pos, java.sql.Types.NULL)
                is UUID -> stmt.setObject(pos, value)
                is String -> stmt.setString(pos, value)
                is Long -> stmt.setLong(pos, value)
                is Int -> stmt.setInt(pos, value)
                is Boolean -> stmt.setBoolean(pos, value)
                is LocalDateTime -> stmt.setObject(pos, value)
                is java.time.Instant -> stmt.setObject(pos, value.atOffset(ZoneOffset.UTC).toLocalDateTime())
                is Number -> stmt.setLong(pos, value.toLong())
                else -> stmt.setString(pos, value.toString())
            }
        }
    }

    private fun getObject(rs: ResultSet, i: Int): Any? {
        val obj = rs.getObject(i)
        if (rs.wasNull()) return null
        return when (obj) {
            is java.sql.Timestamp -> obj.toLocalDateTime()
            is java.sql.Date -> obj.toLocalDate()
            is java.util.UUID -> obj
            is java.math.BigDecimal -> obj.toLong()
            else -> obj
        }
    }

    fun nowLdt(): LocalDateTime = LocalDateTime.now(ZoneOffset.UTC)
}
