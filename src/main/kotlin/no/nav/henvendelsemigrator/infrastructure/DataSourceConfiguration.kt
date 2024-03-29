package no.nav.henvendelsemigrator.infrastructure

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import no.nav.henvendelsemigrator.log
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import javax.sql.DataSource

data class DbConfig(
    val url: String,
    val username: String,
    val password: String
) {
    companion object {
        fun load(name: String) = DbConfig(
            url = readFileContent("/var/run/secrets/nais.io/${name}_config/jdbc_url"),
            username = readFileContent("/var/run/secrets/nais.io/${name}_user/username"),
            password = readFileContent("/var/run/secrets/nais.io/${name}_user/password")
        )

        private fun readFileContent(path: String): String = readFileContent(Paths.get(path))

        private fun readFileContent(path: Path): String {
            if (!Files.isRegularFile(path)) {
                throw IllegalStateException("Fant ikke fil $path")
            }
            return Files.readAllLines(path).joinToString("\n")
        }
    }
}

sealed class HealthcheckedDataSource {
    fun getOrThrow(): DataSource {
        return when (this) {
            is Ok -> this.dataSource
            is Error -> throw this.throwable
        }
    }
    class Ok(val dataSource: DataSource) : HealthcheckedDataSource()
    class Error(val throwable: Throwable) : HealthcheckedDataSource()
}

object DataSourceConfiguration {
    private val datasources: MutableMap<String, HealthcheckedDataSource> = mutableMapOf()
    fun getDatasource(dbConfig: DbConfig): HealthcheckedDataSource {
        return datasources.computeIfAbsent(dbConfig.url) {
            try {
                val config = HikariConfig()
                config.jdbcUrl = dbConfig.url
                config.username = dbConfig.username
                config.password = dbConfig.password
                config.minimumIdle = 2
                config.maximumPoolSize = 100
                config.connectionTimeout = 5000
                config.maxLifetime = 30000
                config.isAutoCommit = false

                log.info("Creating DataSource to: ${config.jdbcUrl}")
                HealthcheckedDataSource.Ok(HikariDataSource(config))
            } catch (throwable: Throwable) {
                HealthcheckedDataSource.Error(throwable)
            }
        }
    }
}
