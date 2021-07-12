package no.nav.henvendelsemigrator.utils.kafka

import kotlinx.coroutines.runBlocking
import no.nav.henvendelsemigrator.infrastructure.health.Healthcheck
import no.nav.henvendelsemigrator.infrastructure.health.HealthcheckResult
import org.apache.kafka.clients.consumer.ConsumerRecords
import org.apache.kafka.clients.consumer.KafkaConsumer
import java.time.Duration
import java.util.*

class HealthcheckableKafkaConsumer<K, V>(properties: Properties) : KafkaConsumer<K, V>(properties), Healthcheck {
    private val timer: Timer = Timer()
    private val healthcheck = Healthcheck.byRunning("KafkaConsumer") {
        this.groupMetadata()
    }
    private var healthcheckResult: HealthcheckResult = runBlocking { healthcheck.check() }
    private var requestHealthcheck = false

    init {
        timer.scheduleAtFixedRate(
            object : TimerTask() {
                override fun run() {
                    requestHealthcheck = true
                }
            },
            30_000, 30_000
        )
    }

    override fun poll(timeout: Duration?): ConsumerRecords<K, V> {
        if (requestHealthcheck) {
            no.nav.henvendelsemigrator.log.info("Running KafkaConsumer requested healthcheck")
            healthcheckResult = runBlocking { healthcheck.check() }
            requestHealthcheck = false
        }
        return super.poll(timeout)
    }

    override suspend fun check(): HealthcheckResult {
        return healthcheckResult
    }
}
