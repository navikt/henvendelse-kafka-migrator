package no.nav.henvendelsemigrator.tasks

import kotliquery.Row
import no.nav.henvendelsemigrator.domain.HenvendelseType
import no.nav.henvendelsemigrator.infrastructure.HealthcheckedDataSource
import no.nav.henvendelsemigrator.infrastructure.health.Healthcheck
import no.nav.henvendelsemigrator.infrastructure.health.HealthcheckResult
import no.nav.henvendelsemigrator.utils.executeQuery
import no.nav.henvendelsemigrator.utils.kafka.KafkaUtils
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord

class ReadExistingHenvendelseIdsTask(
    val henvendelseDb: HealthcheckedDataSource,
    val kafka: KafkaProducer<String, String>
) : SimpleTask() {
    override val name: String = requireNotNull(ReadExistingHenvendelseIdsTask::class.simpleName)
    override val description: String = """
        Leser alle relevante henvendelse_ider fra henvendelseDB. 
    """.trimIndent()
    private var isDone: Boolean = false
    private var processed: Int = 0

    private val henvendelsetyper = HenvendelseType
        .values()
        .joinToString(", ") { "'$it'" }

    override suspend fun runTask() {
        println("Starting $name")
        val query = "SELECT henvendelse_id FROM HENVENDELSE WHERE type in ($henvendelsetyper)"
        executeQuery(henvendelseDb, query) { rs ->
            Row(rs).forEach {
                val henvendelseId = it.string("henvendelse_id")
                kafka.send(ProducerRecord(KafkaUtils.endringsloggTopic, henvendelseId, henvendelseId))
                processed++
            }
        }

        isDone = true
        println("$name is done")
    }

    override suspend fun reset() {
        isDone = false
        processed = 0
    }

    override fun status() = TaskStatus(
        name = name,
        description = description,
        startingTime = startingTime,
        endTime = endTime,
        isRunning = isRunning(),
        isDone = isDone,
        processed = processed
    )

    override fun toHealtchCheck() = Healthcheck {
        val start = System.currentTimeMillis()
        try {
            executeQuery(
                henvendelseDb, "SELECT henvendelse_id FROM HENVENDELSE WHERE type in ($henvendelsetyper) AND (behandlingsid = ? OR behandlingsid = ?)",
                {
                    it.setString(1, "11pg") // LocalTest
                    it.setString(2, "1000C4YTW") // OracleTest
                },
                { rs ->
                    rs.next()
                    val henvendelseId = rs.getString("henvendelse_id")
                    if (henvendelseId == null) {
                        HealthcheckResult.Error(name, System.currentTimeMillis() - start, IllegalStateException("No matching rows found"))
                    } else {
                        HealthcheckResult.Ok(name, System.currentTimeMillis() - start, "Fikk hentet ut henvendelse_id fra henvendelse")
                    }
                }
            )
        } catch (throwable: Throwable) {
            HealthcheckResult.Error(name, System.currentTimeMillis() - start, throwable)
        }
    }
}
