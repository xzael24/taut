package com.taut.db

import com.taut.config.DataSources
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction

/**
 * Initializes Exposed to use the existing HikariCP DataSource.
 * Schema management is exclusively handled by Flyway migrations — Exposed is used ONLY
 * for querying, NOT for schema creation. This avoids schema drift between Flyway SQL
 * and Exposed table definitions.
 */
object Db {
    fun init() {
        Database.connect(DataSources.primary)
        // Schema is managed entirely by Flyway migrations (run in Application.kt).
        // SchemaUtils.createMissingTablesAndColumns() is intentionally NOT called here
        // to prevent schema drift between Exposed definitions and Flyway SQL migrations.
    }
}
