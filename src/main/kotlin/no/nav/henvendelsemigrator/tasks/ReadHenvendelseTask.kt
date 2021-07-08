package no.nav.henvendelsemigrator.tasks

import kotlinx.coroutines.*
import kotliquery.Row
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.henvendelsemigrator.domain.*
import no.nav.henvendelsemigrator.infrastructure.health.Healthcheck
import no.nav.henvendelsemigrator.infrastructure.health.HealthcheckResult
import no.nav.henvendelsemigrator.utils.kafka.KafkaUtils
import no.nav.henvendelsemigrator.utils.toJson
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.time.LocalDateTime
import javax.sql.DataSource

abstract class SimpleTask : Task {
    private var process: Job? = null
    protected var startingTime: LocalDateTime? = null
    protected var endTime: LocalDateTime? = null

    abstract suspend fun runTask()
    abstract suspend fun reset()
    override suspend fun start() {
        if (process != null) throw IllegalStateException("Task $name is already running")
        withContext(Dispatchers.IO) {
            startingTime = LocalDateTime.now()
            endTime = null
            println("Starting $name ${LocalDateTime.now()}")
            // Launching in globalScope as to not force ktor to wait for the jobs completions
            process = GlobalScope.launch {
                reset()
                runTask()
                endTime = LocalDateTime.now()
            }
            println("Started $name ${LocalDateTime.now()}")
        }
    }

    override suspend fun stop() {
        if (process == null) throw IllegalStateException("Task $name is not running")
        process?.cancel()
        process = null
    }

    override fun isRunning(): Boolean = process != null
}

class ReadHenvendelseTask(
    val henvendelseDb: DataSource,
    val henvendelseArkivDb: DataSource,
    val kafka: KafkaProducer<String, String>
) : SimpleTask() {
    override val name: String = requireNotNull(ReadHenvendelseTask::class.simpleName)
    override val description: String = """
        Leser alle henvendelser fra henvendelseDB. 
    """.trimIndent()
    private var isDone: Boolean = false
    private var processed: Int = 0

    fun query(
        dataSource: DataSource,
        query: String,
        setVars: (PreparedStatement) -> Unit,
        process: (ResultSet) -> Unit
    ) {
        using(dataSource.connection.prepareStatement(query)) { statement ->
            statement.fetchSize = 1000
            statement.fetchDirection = ResultSet.FETCH_FORWARD
            setVars(statement)
            process(statement.executeQuery())
        }
    }

    override suspend fun runTask() {
        println("Starting $name")
        query(henvendelseDb, "SELECT * FROM HENVENDELSE", {}) { rs ->
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
        return using(sessionOf(henvendelseArkivDb)) { session ->
            session.run(
                queryOf("SELECT *  FROM ARKIVPOST WHERE arkivpostId in (${params(arkivpostIds.size)})", *arkivpostIds)
                    .map { row -> row.toArkivpost() }
                    .asList
            ).associateBy { it.arkivpostid }
        }
    }

    fun hentVedlegg(henvendelse: List<Henvendelse>): Map<Long, Vedlegg> {
        val arkivpostIds: Array<Long> =
            henvendelse.map { it.arkivpostId?.toLong() }.filterNotNull().distinct().toTypedArray()
        return using(sessionOf(henvendelseArkivDb)) { session ->
            session.run(
                queryOf("SELECT *  FROM VEDLEGG WHERE arkivpostId in (${params(arkivpostIds.size)})", *arkivpostIds)
                    .map { row -> row.toVedlegg() }
                    .asList
            ).associateBy { it.arkivpostid }
        }
    }

    fun hentAlleHendelser(henvendelser: List<Henvendelse>): Map<Long, List<Hendelse>> {
        val arkivpostIds: Array<Long> = henvendelser.map { it.henvendelseId }.distinct().toTypedArray()
        return using(sessionOf(henvendelseDb)) { session ->
            session.run(
                queryOf("SELECT *  FROM hendelse WHERE henvendelse_id in (${params(arkivpostIds.size)})", *arkivpostIds)
                    .map { row -> row.toHendelse() }
                    .asList
            ).groupBy { it.henvendelseId }
        }
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
            var description: String? = null
            query(
                henvendelseDb, "SELECT * FROM HENVENDELSE WHERE behandlingsid = ? OR behandlingsid = ?",
                {
                    it.setString(1, "11pg") // LocalTest
                    it.setString(2, "1000C4YTW") // OracleTest
                },
                { rs ->
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
                }
            )
            HealthcheckResult.Ok(name, System.currentTimeMillis() - start, description)
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
