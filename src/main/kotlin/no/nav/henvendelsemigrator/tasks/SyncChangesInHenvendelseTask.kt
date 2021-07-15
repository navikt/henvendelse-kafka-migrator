package no.nav.henvendelsemigrator.tasks

import kotlinx.coroutines.delay
import kotliquery.Row
import no.nav.henvendelsemigrator.domain.HendelseType
import no.nav.henvendelsemigrator.domain.HenvendelseType
import no.nav.henvendelsemigrator.infrastructure.HealthcheckedDataSource
import no.nav.henvendelsemigrator.infrastructure.health.Healthcheck
import no.nav.henvendelsemigrator.introspect.SetLastProcessedHendelse
import no.nav.henvendelsemigrator.log
import no.nav.henvendelsemigrator.utils.executeQuery
import no.nav.henvendelsemigrator.utils.kafka.KafkaUtils
import no.nav.henvendelsemigrator.utils.minutesInMillies
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord

data class Hendelse(val id: Long, val henvendelseId: String)

const val SIST_PROSESSERT_HENDELSE = "SIST_PROSESSERT_HENDELSE"

class SyncChangesInHenvendelseTask(
    val henvendelseDb: HealthcheckedDataSource,
    val producer: KafkaProducer<String, String>
) : SimpleTask() {
    override val name: String = "Fase 2 - Lytt til endringer"
    override val description: String = """
        Holder styr på siste synkroniserte henvendelse vha endringer i hendelse-tabellen.
        Siste prosesserte hendelse er lagret i migreringmetadata-tabellen.
    """.trimIndent()
    private var processed: Int = 0

    private val henvendelsetyper = HenvendelseType
        .values()
        .joinToString(", ") { "'$it'" }

    private val hendelsetyper = HendelseType
        .values()
        .joinToString(", ") { "'$it'" }

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
                Row(rs)
                    .map { it.stringOrNull("key") to it.stringOrNull("value") }
                    .toMap()
            }
        )
        val sistProsesserteHendelse: Long = migreringData[SIST_PROSESSERT_HENDELSE]?.toLong() ?: return emptyList()

        return executeQuery(
            dataSource = henvendelseDb,
            query = """
                SELECT hendelse.id, hendelse.henvendelse_id FROM hendelse hendelse
                JOIN henvendelse henvendelse ON (hendelse.henvendelse_id = henvendelse.henvendelse_id)
                WHERE hendelse.id > ?
                AND hendelse.type IN ($hendelsetyper)
                AND henvendelse.type IN ($henvendelsetyper)
            """.trimIndent(),
            setVars = { stmt -> stmt.setLong(1, sistProsesserteHendelse) },
            process = { rs ->
                Row(rs)
                    .map { row ->
                        Hendelse(
                            row.long("id"),
                            row.string("henvendelse_id")
                        )
                    }
                    .toList()
            }
        )
    }

    private fun lagreSistLesteHendelse(hendelse: Hendelse) {
        SetLastProcessedHendelse.Task(henvendelseDb).action(
            SetLastProcessedHendelse.Input(hendelse.id)
        )
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
            Får hentet ut nødvendig data fra migreringsmetadata og hendelse tabell
        """.trimIndent()
    ) {
        val migreringData = executeQuery(
            dataSource = henvendelseDb,
            query = "SELECT * FROM migreringmetadata",
            process = { rs ->
                Row(rs).map {
                    it.stringOrNull("key") to it.stringOrNull("value")
                }.toMap()
            }
        )
        requireNotNull(migreringData[SIST_PROSESSERT_HENDELSE]) {
            "Tabell må inneholde '$SIST_PROSESSERT_HENDELSE'"
        }
        hentHenvendelseIderSomSkalSynkroniseres()
    }
}
