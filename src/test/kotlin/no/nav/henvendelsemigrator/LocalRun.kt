package no.nav.henvendelsemigrator

import kotlinx.coroutines.runBlocking
import no.nav.henvendelsemigrator.infrastructure.AuthConfig
import no.nav.henvendelsemigrator.infrastructure.AutoKonfigAware
import no.nav.henvendelsemigrator.infrastructure.DataSourceConfiguration
import no.nav.henvendelsemigrator.infrastructure.DbConfig
import no.nav.henvendelsemigrator.infrastructure.health.toHealthcheck
import no.nav.henvendelsemigrator.utils.kafka.KafkaUtils
import org.apache.kafka.clients.producer.KafkaProducer
import java.util.*

class LocalConfig : Config, AutoKonfigAware() {
    override val henvendelseDbConfig = DbConfig(
        url = "jdbc:postgresql://localhost:7060/henvendelse",
        username = "henvendelse",
        password = "henvendelse"
    )
    override val henvendelseArkivDbConfig = DbConfig(
        url = "jdbc:postgresql://localhost:7061/henvendelsearkiv",
        username = "henvendelsearkiv",
        password = "henvendelsearkiv"
    )
    override val authConfig: AuthConfig = AuthConfig.UseMock("Z999999")
    override val kafkaSecurityConfig: (properties: Properties) -> Unit = {}
    override val kafkaBrokers = "localhost:9092"
    override val autoStartProcessChangesTask: Boolean = false
    override val autoStartSyncChangesInHenvendelseTask: Boolean = false
}

fun main() {
    val config = LocalConfig()
    when (true) {
        true -> runApplication(config)
        else -> {
            val henvendelseDb = DataSourceConfiguration.getDatasource(config.henvendelseDbConfig)
            val henvendelseArkivDb = DataSourceConfiguration.getDatasource(config.henvendelseArkivDbConfig)
            val kafkaProducer = KafkaProducer<String, String>(
                KafkaUtils.producerConfig("henvendelse-kafka-migrator-producer", config)
            )

            runBlocking {
                println(henvendelseDb.toHealthcheck("henvendelse").check())
                println(henvendelseArkivDb.toHealthcheck("henvendelsearkiv").check())
                println(kafkaProducer.toHealthcheck(KafkaUtils.henvendelseTopic).check())
                println(kafkaProducer.toHealthcheck(KafkaUtils.endringsloggTopic).check())
            }
        }
    }
}
