package no.nav.henvendelsemigrator.tasks

import kotliquery.Row
import no.nav.henvendelsemigrator.domain.*
import no.nav.henvendelsemigrator.infrastructure.HealthcheckedDataSource
import no.nav.henvendelsemigrator.infrastructure.health.Healthcheck
import no.nav.henvendelsemigrator.infrastructure.health.HealthcheckResult
import no.nav.henvendelsemigrator.utils.executeQuery
import no.nav.henvendelsemigrator.utils.kafka.KafkaUtils
import no.nav.henvendelsemigrator.utils.toJson
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord

class ReadHenvendelseTask(
    val henvendelseDb: HealthcheckedDataSource,
    val henvendelseArkivDb: HealthcheckedDataSource,
    val kafka: KafkaProducer<String, String>
) : SimpleTask() {
    override val name: String = requireNotNull(ReadHenvendelseTask::class.simpleName)
    override val description: String = """
        Leser alle henvendelser fra henvendelseDB. 
    """.trimIndent()
    private var isDone: Boolean = false
    private var processed: Int = 0

    override suspend fun runTask() {
        println("Starting $name")
        executeQuery(henvendelseDb, "SELECT * FROM HENVENDELSE") { rs ->
            val iterator: Sequence<Row> = Row(rs)
            val henvendelseBuffer: MutableList<Henvendelse> = mutableListOf()
            for (it in iterator) {
                if (!isRunning()) break
                henvendelseBuffer.add(it.toHenvendelse())
                if (henvendelseBuffer.size == 1000) {
                    prosessBuffer(henvendelseBuffer)
                }
            }
            if (henvendelseBuffer.isNotEmpty()) {
                prosessBuffer(henvendelseBuffer)
            }
        }

        isDone = true
        println("$name is done")
    }

    fun prosessBuffer(henvendelseBuffer: MutableList<Henvendelse>) {
        val arkivposter: Map<Long, Arkivpost> = hentArkivposter(henvendelseBuffer)
        val vedlegg: Map<Long, Vedlegg> = hentVedlegg(henvendelseBuffer)
        val hendelser: Map<Long, List<Hendelse>> = hentAlleHendelser(henvendelseBuffer)
        henvendelseBuffer.forEach { henvendelse ->
            val record = mapOf(
                "henvendelse" to henvendelse,
                "arkivpost" to arkivposter[henvendelse.arkivpostId?.toLong()],
                "vedlegg" to vedlegg[henvendelse.arkivpostId?.toLong()],
                "hendelser" to hendelser[henvendelse.henvendelseId]
            ).toJson()
            kafka.send(ProducerRecord(KafkaUtils.henvendelseTopic, henvendelse.henvendelseId.toString(), record))
            processed++
        }
        henvendelseBuffer.clear()
    }

    fun hentArkivposter(henvendelse: List<Henvendelse>): Map<Long, Arkivpost> {
        val arkivpostIds: Array<Long> =
            henvendelse.map { it.arkivpostId?.toLong() }.filterNotNull().distinct().toTypedArray()
        return executeQuery(
            dataSource = henvendelseArkivDb,
            query = "SELECT *  FROM ARKIVPOST WHERE arkivpostId in (${params(arkivpostIds.size)})",
            setVars = { stmt ->
                arkivpostIds.forEachIndexed { index, arkivpostId ->
                    stmt.setLong(index + 1, arkivpostId)
                } 
            },
            process = { rs ->
                Row(rs).map { it.toArkivpost() }.toList()
            }
        ).associateBy { it.arkivpostid }
    }

    fun hentVedlegg(henvendelse: List<Henvendelse>): Map<Long, Vedlegg> {
        val arkivpostIds: Array<Long> =
            henvendelse.map { it.arkivpostId?.toLong() }.filterNotNull().distinct().toTypedArray()
        return executeQuery(
            dataSource = henvendelseArkivDb,
            query = "SELECT * FROM VEDLEGG WHERE arkivpostId in (${params(arkivpostIds.size)})",
            setVars = { stmt ->
                arkivpostIds.forEachIndexed { index, arkivpostId ->
                    stmt.setLong(index + 1, arkivpostId)
                } 
            },
            process = { rs ->
                Row(rs).map { it.toVedlegg() }.toList()
            }
        ).associateBy { it.arkivpostid }
    }

    fun hentAlleHendelser(henvendelser: List<Henvendelse>): Map<Long, List<Hendelse>> {
        val henvendelseIds: Array<Long> = henvendelser.map { it.henvendelseId }.distinct().toTypedArray()
        return executeQuery(
            dataSource = henvendelseDb,
            query = "SELECT *  FROM hendelse WHERE henvendelse_id in (${params(henvendelseIds.size)})",
            setVars = { stmt ->
                henvendelseIds.forEachIndexed { index, arkivpostId ->
                    stmt.setLong(index + 1, arkivpostId)
                } 
            },
            process = { rs ->
                Row(rs).map { it.toHendelse() }.toList()
            }
        ).groupBy { it.henvendelseId }
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
                henvendelseDb, "SELECT * FROM HENVENDELSE WHERE behandlingsid = ? OR behandlingsid = ?",
                {
                    it.setString(1, "11pg") // LocalTest
                    it.setString(2, "1000C4YTW") // OracleTest
                },
                { rs ->
                    var description: String? = null
                    for (it in Row(rs)) {
                        val henvendelse = it.toHenvendelse()
                        val henvendelseBuffer = listOf(henvendelse)
                        val arkivposter: Map<Long, Arkivpost> = hentArkivposter(henvendelseBuffer)
                        val vedlegg: Map<Long, Vedlegg> = hentVedlegg(henvendelseBuffer)
                        val hendelser: Map<Long, List<Hendelse>> = hentAlleHendelser(henvendelseBuffer)
                        description = mapOf(
                            "henvendelse" to henvendelse,
                            "arkivpost" to arkivposter[henvendelse.arkivpostId?.toLong()],
                            "vedlegg" to vedlegg[henvendelse.arkivpostId?.toLong()],
                            "hendelser" to hendelser[henvendelse.henvendelseId]
                        ).toJson()
                    }
                    if (description == null) {
                        HealthcheckResult.Error(name, System.currentTimeMillis() - start, IllegalStateException("No matching rows found"))
                    } else {
                        HealthcheckResult.Ok(name, System.currentTimeMillis() - start, description)
                    }
                }
            )
        } catch (throwable: Throwable) {
            HealthcheckResult.Error(name, System.currentTimeMillis() - start, throwable)
        }
    }

    fun params(l: Int) = "?"
        .repeat(l)
        .split("")
        .filter { it.isNotEmpty() }
        .joinToString(", ")
}
