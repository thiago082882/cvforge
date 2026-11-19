package com.cvforge.server.db

import com.cvforge.server.config.AppConfig
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction

object DatabaseFactory {
    fun connect(config: AppConfig) {
        val hikari = HikariConfig().apply {
            jdbcUrl = config.dbUrl
            username = config.dbUser
            password = config.dbPassword
            maximumPoolSize = config.dbPoolSize
            minimumIdle = 2
            isAutoCommit = false
            transactionIsolation = "TRANSACTION_READ_COMMITTED"
            driverClassName = "org.postgresql.Driver"
            validate()
        }
        Database.connect(HikariDataSource(hikari))
        // Para produção séria, troque por migrações versionadas (Flyway).
        transaction {
            SchemaUtils.createMissingTablesAndColumns(UsersTable, ProfilesTable, CurriculumsTable)
        }
    }
}

/** Transação suspensa em Dispatchers.IO — não bloqueia as threads do Netty. */
suspend fun <T> dbQuery(block: suspend Transaction.() -> T): T =
    newSuspendedTransaction(Dispatchers.IO) { block() }
