package no.nav.henvendelsemigrator

import dev.nohus.autokonfig.types.StringSetting
import io.ktor.http.content.*
import io.ktor.routing.*
import no.nav.henvendelsemigrator.infrastructure.*
import no.nav.henvendelsemigrator.infrastructure.health.Healthcheck
import no.nav.henvendelsemigrator.infrastructure.health.toHealthcheck
import no.nav.henvendelsemigrator.introspect.ProcessHenvendelseId
import no.nav.henvendelsemigrator.introspect.ReadKafkaTopic
import no.nav.henvendelsemigrator.introspect.introspectRoutes
import no.nav.henvendelsemigrator.tasks.*
import no.nav.henvendelsemigrator.utils.kafka.HealthcheckableKafkaConsumer
import no.nav.henvendelsemigrator.utils.kafka.KafkaUtils
import org.apache.kafka.clients.producer.KafkaProducer
import org.slf4j.LoggerFactory
import java.util.*

val appname = "henvendelse-kafka-migrator"
val log = LoggerFactory.getLogger(appname)

interface Config {
    val henvendelseDbConfig: DbConfig
    val henvendelseArkivDbConfig: DbConfig
    val authConfig: AuthConfig
    val kafkaSecurityConfig: (properties: Properties) -> Unit
    val kafkaBrokers: String
}

fun runApplication(config: Config) {
    val henvendelseDb = DataSourceConfiguration.getDatasource(config.henvendelseDbConfig)
    val henvendelseArkivDb = DataSourceConfiguration.getDatasource(config.henvendelseArkivDbConfig)
    val kafkaProducer = KafkaProducer<String, String>(
        KafkaUtils.producerConfig("henvendelse-kafka-migrator-producer", config)
    )
    val kafkaConsumer = HealthcheckableKafkaConsumer<String, String>(
        KafkaUtils.consumerConfig("henvendelse-kafka-migrator-consumer", "henvendelse-kafka-migrator-consumer", config)
    )
    val readExistingHenvendelseIdsTask = ReadExistingHenvendelseIdsTask(henvendelseDb, kafkaProducer)
    val processChangesTask = ProcessChangesTask(
        autoStart = true,
        consumer = kafkaConsumer,
        producer = kafkaProducer,
        henvendelseDb = henvendelseDb,
        henvendelseArkivDb = henvendelseArkivDb
    )

    val healthchecks: List<Healthcheck> = listOf(
        henvendelseDb.toHealthcheck("henvendelse"),
        henvendelseArkivDb.toHealthcheck("henvendelsearkiv"),
        kafkaConsumer,
        kafkaProducer.toHealthcheck(KafkaUtils.endringsloggTopic),
        kafkaProducer.toHealthcheck(KafkaUtils.henvendelseTopic),
        readExistingHenvendelseIdsTask.toHealtchCheck(),
        processChangesTask.toHealtchCheck()
    )

    HttpServer.create(appname, 7075) { state ->
        standardAppSetup(
            applicationState = state,
            healthchecks = healthchecks,
            authConfig = config.authConfig
        )
        routing {
            route(appname) {
                static {
                    resources("webapp")
                    defaultResource("index.html", "webapp")
                }
                taskRoutes(readExistingHenvendelseIdsTask, processChangesTask)
                introspectRoutes(
                    ProcessHenvendelseId(processChangesTask),
                    ReadKafkaTopic(config)
                )
            }
        }
    }.start(wait = true)
}

class NaisConfig : Config, AutoKonfigAware() {
    override val henvendelseDbConfig = DbConfig.load("db_henvendelse")
    override val henvendelseArkivDbConfig = DbConfig.load("db_henvendelsearkiv")
    override val authConfig: AuthConfig = AuthConfig.JwksUrl("")
    override val kafkaSecurityConfig: (properties: Properties) -> Unit = KafkaUtils::aivenSecurityConfig
    override val kafkaBrokers by StringSetting()
}
fun main() {
    val config = NaisConfig()
    runApplication(config)
}
