package com.taut.config

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource

/**
 * Initializes the HikariCP connection pool for PostgreSQL.
 */
fun configureDatabase(config: DatabaseConfig): HikariDataSource {
    val hikariConfig = HikariConfig().apply {
        jdbcUrl = config.url
        username = config.user
        password = config.password

        // Auto-detect JDBC driver from URL
        driverClassName = when {
            config.url.startsWith("jdbc:h2:") -> "org.h2.Driver"
            config.url.startsWith("jdbc:postgresql:") -> "org.postgresql.Driver"
            config.url.startsWith("jdbc:mysql:") -> "com.mysql.cj.jdbc.Driver"
            else -> throw IllegalArgumentException("Unsupported database URL: ${config.url}")
        }

        maximumPoolSize = config.maximumPoolSize
        minimumIdle = config.minimumIdle
        idleTimeout = config.idleTimeout
        connectionTimeout = config.connectionTimeout
        maxLifetime = config.maxLifetime

        // Performance settings
        addDataSourceProperty("cachePrepStmts", "true")
        addDataSourceProperty("prepStmtCacheSize", "250")
        addDataSourceProperty("prepStmtCacheSqlLimit", "2048")
        addDataSourceProperty("useServerPrepStmts", "true")
        addDataSourceProperty("rewriteBatchedInserts", "true")

        // Connection validation
        connectionTestQuery = "SELECT 1"
        isAutoCommit = false
        transactionIsolation = "TRANSACTION_READ_COMMITTED"
    }

    return HikariDataSource(hikariConfig)
}

/**
 * Provides a lazy-initialized singleton reference to the Hikari DataSource.
 * In a real deployment this would be managed by a DI framework (Koin/Hilt).
 */
object DataSources {
    lateinit var primary: HikariDataSource
        private set

    fun init(config: DatabaseConfig) {
        primary = configureDatabase(config)
    }
}
