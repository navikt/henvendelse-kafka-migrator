package no.nav.henvendelsemigrator.tasks

import kotlinx.coroutines.delay
import kotliquery.Row
import kotliquery.using
import no.nav.henvendelsemigrator.infrastructure.HealthcheckedDataSource
import no.nav.henvendelsemigrator.infrastructure.health.Healthcheck
import no.nav.henvendelsemigrator.log
import no.nav.henvendelsemigrator.utils.executeQuery
import no.nav.henvendelsemigrator.utils.kafka.KafkaUtils
import no.nav.henvendelsemigrator.utils.minutesInMillies
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord

data class Hendelse(val id: Long, val henvendelseId: String)
private const val SIST_PROSESSERT_HENDELSE = "SIST_PROSESSERT_HENDELSE"
class SyncChangesInHenvendelseTask(
    val henvendelseDb: HealthcheckedDataSource,
    val producer: KafkaProducer<String, String>
) : SimpleTask() {
    override val name: String = requireNotNull(SyncChangesInHenvendelseTask::class.simpleName)
    override val description: String = """
        Holder styr på siste synkroniserte henvendelse vha endringer i hendelse-tabellen.
        Siste prosesserte hendelse er lagret i migreringmetadata-tabellen.
    """.trimIndent()
    private var processed: Int = 0

    override suspend fun runTask() {
        while (isRunning()) {
            log.info("Resynkroniserer hendelser")
            val hendelser: List<Hendelse> = hentHenvendelseIderSomSkalSynkroniseres()
            hendelser.forEach {
                producer.send(ProducerRecord(KafkaUtils.endringsloggTopic, it.henvendelseId, it.henvendelseId))
                processed++
            }
            val sisteHendelse = hendelser.lastOrNull()
            sisteHendelse?.let { lagreSistLesteHendelse(it) }
            log.info("Synkroniserte ${hendelser.size} hendelser, hvorav siste var $sisteHendelse. Venter ett minutt til neste gang.")
            delay(1.minutesInMillies)
        }
    }

    private fun hentHenvendelseIderSomSkalSynkroniseres(): List<Hendelse> {
        val migreringData = executeQuery(
            dataSource = henvendelseDb,
            query = "SELECT * FROM migreringmetadata",
            process = { rs ->
                Row(rs).map { it.stringOrNull("key") to it.stringOrNull("value") }
            }
        ).toMap()
        val sistProsesserteHendelse: Long = migreringData[SIST_PROSESSERT_HENDELSE]?.toLong() ?: return emptyList()

        return executeQuery(
            dataSource = henvendelseDb,
            query = "SELECT * FROM hendelse WHERE id > ?",
            setVars = { stmt -> stmt.setLong(1, sistProsesserteHendelse) },
            process = { rs ->
                Row(rs).map { row ->
                    Hendelse(
                        row.long("id"),
                        row.string("henvendelse_id")
                    )
                }
            }
        ).toList()
    }

    private fun lagreSistLesteHendelse(hendelse: Hendelse) {
        using(henvendelseDb.getOrThrow().connection) { connection ->
            val stmt = connection.prepareStatement("UPDATE migreringmetadata SET value = ? WHERE key = '$SIST_PROSESSERT_HENDELSE'")
            stmt.setString(1, hendelse.id.toString())
            stmt.execute()
            connection.commit()
        }
    }

    override suspend fun reset() {
        processed = 0
    }

    override fun status() = TaskStatus(
        name = name,
        description = description,
        startingTime = startingTime,
        endTime = endTime,
        isRunning = isRunning(),
        isDone = false,
        processed = processed
    )

    override fun toHealtchCheck() = Healthcheck.byRunning(
        name = name,
        description = """
            Får hentet ut nødvendig data fra migreringsmetadata tabell
        """.trimIndent()
    ) {
        val migreringData = executeQuery(
            dataSource = henvendelseDb,
            query = "SELECT * FROM migreringmetadata",
            process = { rs ->
                Row(rs).map { it.stringOrNull("key") to it.stringOrNull("value") }
            }
        ).toMap()
        requireNotNull(migreringData[SIST_PROSESSERT_HENDELSE]) {
            "Tabell må innehold nøkkel for '$SIST_PROSESSERT_HENDELSE'"
        }
    }
}
