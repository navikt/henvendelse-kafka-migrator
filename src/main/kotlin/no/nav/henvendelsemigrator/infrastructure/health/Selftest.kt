package no.nav.henvendelsemigrator.infrastructure.health

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.zaxxer.hikari.HikariDataSource
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.henvendelsemigrator.infrastructure.HealthcheckedDataSource
import org.apache.kafka.clients.producer.KafkaProducer

fun interface Healthcheck {
    suspend fun check(): HealthcheckResult
    companion object {
        fun byRunning(name: String, description: String? = null, block: suspend () -> Any) = Healthcheck {
            val start = System.currentTimeMillis()
            try {
                block()
                HealthcheckResult.Ok(name, System.currentTimeMillis() - start, description)
            } catch (throwable: Throwable) {
                HealthcheckResult.Error(name, System.currentTimeMillis() - start, throwable)
            }
        }
    }
}

class StacktraceSerializer : JsonSerializer<Throwable>() {
    override fun serialize(value: Throwable, gen: JsonGenerator, serializers: SerializerProvider) {
        gen.writeString(value.stackTraceToString())
    }
}
sealed class HealthcheckResult(val name: String, val time: Long) {
    class Ok(name: String, time: Long, val description: String? = null) : HealthcheckResult(name, time) {
        override fun toString() = "HealthcheckResult.Ok(name='$name', description=$description, time=$time)"
    }
    class Error(
        name: String,
        time: Long,
        @JsonSerialize(using = StacktraceSerializer::class) val throwable: Throwable
    ) : HealthcheckResult(name, time) {
        override fun toString() = "HealthcheckResult.Error(name='$name', time=$time, throwable=$throwable)"
    }
}

fun HealthcheckedDataSource.toHealthcheck(database: String) = Healthcheck.byRunning("Database - $database") {
    when (this) {
        is HealthcheckedDataSource.Error -> throw this.throwable
        is HealthcheckedDataSource.Ok -> {
            val isPostgresql = this.dataSource is HikariDataSource && this.dataSource.jdbcUrl.contains(":postgresql:")
            val query = when (isPostgresql) {
                true -> "SELECT CURRENT_TIME"
                else -> "SELECT SYSDATE FROM DUAL"
            }
            using(sessionOf(this.dataSource)) { session ->
                session.run(queryOf(query).asExecute)
            }
        }
    }
}

fun KafkaProducer<*, *>.toHealthcheck(topic: String) = Healthcheck.byRunning("KafkaProducer - $topic") {
    this.partitionsFor(topic)
}
