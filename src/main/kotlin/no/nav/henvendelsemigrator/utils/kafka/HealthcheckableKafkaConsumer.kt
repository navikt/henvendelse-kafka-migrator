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
    private var lastHealthcheck = System.currentTimeMillis()
    private var requestHealthcheck = false
    private val healthcheckInterval = 30_000L

    init {
        timer.scheduleAtFixedRate(
            object : TimerTask() {
                override fun run() {
                    val now = System.currentTimeMillis()
                    if (now - lastHealthcheck > 4 * healthcheckInterval) {
                        // Assume polling is not active, and it's safe to check directly
                        runBlocking {
                            healthcheckResult = healthcheck.check()
                            lastHealthcheck = now
                        }
                    } else {
                        requestHealthcheck = true
                    }
                }
            },
            healthcheckInterval, healthcheckInterval
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
