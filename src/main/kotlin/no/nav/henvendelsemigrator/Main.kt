package no.nav.henvendelsemigrator

import dev.nohus.autokonfig.types.StringSetting
import io.ktor.http.content.*
import io.ktor.routing.*
import no.nav.henvendelsemigrator.infrastructure.*
import no.nav.henvendelsemigrator.infrastructure.health.Healthcheck
import no.nav.henvendelsemigrator.infrastructure.health.toHealthcheck
import no.nav.henvendelsemigrator.tasks.ProcessChangesTask
import no.nav.henvendelsemigrator.tasks.ReadExistingHenvendelseIdsTask
import no.nav.henvendelsemigrator.tasks.TaskManager
import no.nav.henvendelsemigrator.tasks.taskRoutes
import no.nav.henvendelsemigrator.utils.kafka.KafkaUtils
import org.apache.kafka.clients.consumer.KafkaConsumer
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
    val kafkaConsumer = KafkaConsumer<String, String>(
        KafkaUtils.consumerConfig("henvendelse-kafka-migrator-consumer", "henvendelse-kafka-migrator-consumer", config)
    )
    val taskManager = TaskManager(
        ReadExistingHenvendelseIdsTask(henvendelseDb, kafkaProducer),
        ProcessChangesTask(kafkaConsumer, kafkaProducer, henvendelseDb, henvendelseArkivDb)
    )

    val healthchecks: List<Healthcheck> = listOf(
        henvendelseDb.toHealthcheck("henvendelse"),
        henvendelseArkivDb.toHealthcheck("henvendelsearkiv"),
        kafkaProducer.toHealthcheck(KafkaUtils.henvendelseTopic),
        kafkaProducer.toHealthcheck(KafkaUtils.endringsloggTopic),
        kafkaConsumer.toHealthcheck(KafkaUtils.endringsloggTopic),
        *taskManager.taskmap.values.map { it.toHealtchCheck() }.toTypedArray()
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
                taskRoutes(taskManager)
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
