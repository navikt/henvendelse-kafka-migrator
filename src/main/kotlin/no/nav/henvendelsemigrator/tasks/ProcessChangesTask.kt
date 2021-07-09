package no.nav.henvendelsemigrator.tasks

import kotlinx.coroutines.*
import kotliquery.Row
import no.nav.henvendelsemigrator.domain.*
import no.nav.henvendelsemigrator.infrastructure.HealthcheckedDataSource
import no.nav.henvendelsemigrator.infrastructure.health.Healthcheck
import no.nav.henvendelsemigrator.utils.XMLParser
import no.nav.henvendelsemigrator.utils.executeQuery
import no.nav.henvendelsemigrator.utils.kafka.KafkaUtils
import no.nav.henvendelsemigrator.utils.paramlist
import no.nav.henvendelsemigrator.utils.toJson
import org.apache.kafka.clients.consumer.ConsumerRecords
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import java.nio.charset.Charset
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId

class ProcessChangesTask(
    val consumer: KafkaConsumer<String, String>,
    val producer: KafkaProducer<String, String>,
    val henvendelseDb: HealthcheckedDataSource,
    val henvendelseArkivDb: HealthcheckedDataSource,
) : Task {
    override val name = requireNotNull(ProcessChangesTask::class.simpleName)
    override val description = """
        Leser kafka-meldinger fra endringslogg, henter relevant informasjon og dytter det til ny kafka topic
    """.trimIndent()
    private var process: Job? = null
    private var startingTime: LocalDateTime? = null
    private var endTime: LocalDateTime? = null
    private var processed: Int = 0
    private val xmlParser = XMLParser()

    override suspend fun start() {
        if (process != null) throw IllegalStateException("Task $name is already running")
        withContext(Dispatchers.IO) {
            println("Starting $name ${LocalDateTime.now()}")
            startingTime = LocalDateTime.now()
            endTime = null
            consumer.subscribe(listOf(KafkaUtils.endringsloggTopic))
            process = GlobalScope.launch {
                while (isRunning()) {
                    val records: ConsumerRecords<String, String> = consumer.poll(Duration.ofMillis(10_000))
                    process(records)
                    consumer.commitSync()
                }
            }
            println("Started $name ${LocalDateTime.now()}")
        }
    }

    override suspend fun stop() {
        if (process == null) throw IllegalStateException("Task $name is not running")
        consumer.unsubscribe()
        process?.cancel()
        process = null
    }

    override fun isRunning(): Boolean = process != null

    override fun status() = TaskStatus(
        name = name,
        description = description,
        startingTime = startingTime,
        endTime = endTime,
        isRunning = isRunning(),
        isDone = false,
        processed = processed
    )

    override fun toHealtchCheck() = Healthcheck.byRunning(name) {}

    private fun process(records: ConsumerRecords<String, String>) {
        records
            .map { it.value().toLong() }
            .chunked(1000)
            .forEach(::processChunk)
    }

    private fun processChunk(henvendelseIds: List<Long>) {
        val henvendelser: List<OracleHenvendelse> = hentHenvendelser(henvendelseIds)
        val arkivpostIds: List<Long> = henvendelser.mapNotNull { it.arkivpostId?.toLong() }
        val hendelser: Map<Long, List<OracleHendelse>> = hentHendelser(henvendelseIds)
        val arkivposter: Map<Long, OracleArkivpost> = hentArkivposter(arkivpostIds)
        val vedlegg: Map<Long, OracleVedlegg> = hentVedlegg(arkivpostIds)

        henvendelser
            .map { henvendelse ->
                val arkivpostId = henvendelse.arkivpostId?.toLong()
                processHenvendelse(
                    henvendelse = henvendelse,
                    hendelser = hendelser[henvendelse.henvendelseId] ?: emptyList(),
                    arkivpost = arkivpostId?.let { arkivposter[it] },
                    vedlegg = arkivpostId?.let { vedlegg[it] }
                )
            }.forEach {
                producer.send(ProducerRecord(KafkaUtils.henvendelseTopic, it.henvendelseId.toString(), it.toJson()))
            }
    }

    private fun processHenvendelse(
        henvendelse: OracleHenvendelse,
        hendelser: List<OracleHendelse>,
        arkivpost: OracleArkivpost?,
        vedlegg: OracleVedlegg?
    ): Henvendelse {
        val hendelsemap = hendelser.groupBy { it.type }
        val melding = lagMelding(henvendelse, arkivpost, vedlegg)
        val tilknyttning: OracleHendelse? =
            hendelsemap[HendelseKeys.knyttetTilSak]?.firstOrNull()
                ?: hendelsemap[HendelseKeys.knyttetTilTema]?.firstOrNull()
        val kontorsperre: Kontorsperre? = hendelsemap[HendelseKeys.kontorsperret]?.firstOrNull()
            ?.let {
                Kontorsperre(
                    dato = it.dato?.atZone(ZoneId.systemDefault()),
                    aktor = it.aktor,
                    enhet = it.enhet
                )
            }
        val feilsendtHendelse: OracleHendelse? = hendelsemap[HendelseKeys.feilsendt]?.firstOrNull()
        val ferdigstiltUtenSvarHendelse: OracleHendelse? = hendelsemap[HendelseKeys.ferdigstiltUtenSvar]?.firstOrNull()
        val feilsendt: Markering? = feilsendtHendelse
            ?.let {
                Markering(
                    dato = it.dato?.atZone(ZoneId.systemDefault()),
                    aktor = it.aktor
                )
            }
        val ferdigstiltUtenSvar: Markering? = ferdigstiltUtenSvarHendelse
            ?.let {
                Markering(
                    dato = it.dato?.atZone(ZoneId.systemDefault()),
                    aktor = it.aktor
                )
            }

        return Henvendelse(
            henvendelseId = henvendelse.henvendelseId,
            behandlingsId = henvendelse.behandlingsId,
            behandlingskjedeId = henvendelse.behandlingsKjedeId,
            applikasjonsId = null, // Settes aldri av henvendelse
            fnr = arkivpost?.fodselsnummer,
            aktorId = henvendelse.aktor,
            tema = henvendelse.tema,
            behandlingstema = henvendelse.behandlingstema,
            ferdigstiltUtenSvar = ferdigstiltUtenSvarHendelse != null,
            henvendelseType = HenvendelseType.valueOf(requireNotNull(henvendelse.type)),
            eksternAktor = henvendelse.eksternAktor,
            tilknyttetEnhet = henvendelse.tilknyttetEnhet,
            opprettetDato = henvendelse.opprettetdato.atZone(ZoneId.systemDefault()),
            avsluttetDato = henvendelse.innsendtdato?.atZone(ZoneId.systemDefault()),
            lestDato = hendelsemap[HendelseKeys.lest]?.firstOrNull()?.dato?.atZone(ZoneId.systemDefault()),
            kontorsperreEnhet = henvendelse.kontorsperre,
            brukersEnhet = henvendelse.brukersEnhet,
            markertSomFeilsendtAv = feilsendtHendelse?.aktor,
            oppgaveIdGsak = henvendelse.oppgaveIdGsak,
            henvendelseIdGsak = henvendelse.henvendelseIdGsak,
            erTilknyttetAnsatt = henvendelse.erTilknyttetAnsatt,
            gjeldendeTemagruppe = hendelsemap[HendelseKeys.endretTemagruppe]?.lastOrNull()?.verdi?.let { Temagruppe.valueOf(it) },
            journalfortInformasjon = JournalfortInformasjon(
                journalpostId = henvendelse.journalportId,
                journalfortTema = henvendelse.journalfortTema,
                journalfortDato = tilknyttning?.dato?.atZone(ZoneId.systemDefault()),
                journalfortSaksId = henvendelse.journalfortSaksid,
                journalforerNavIdent = tilknyttning?.aktor
            ),
            markeringer = Markeringer(
                kontorsperre = kontorsperre,
                feilsendt = feilsendt,
                ferdigstiltUtenSvar = ferdigstiltUtenSvar
            ),
            korrelasjonsId = henvendelse.korrelasjonsId,
            metadataListe = MetadataListe(melding?.let { listOf(it) })
        )
    }

    fun lagMelding(henvendelse: OracleHenvendelse, arkivpost: OracleArkivpost?, vedlegg: OracleVedlegg?): Melding? {
        if (arkivpost?.status == "KASSERT") {
            return null
        }
        val content: String = if (henvendelse.behandlingsresultat != null) {
            henvendelse.behandlingsresultat
        } else if (vedlegg?.dokument != null) {
            String(vedlegg.dokument, Charset.forName("ISO-8859-1"))
        } else {
            throw IllegalStateException("Fant verken behandlingsresultat eller vedlegg")
        }

        return xmlParser.process(content)
    }

    fun hentHenvendelser(henvendelseIds: List<Long>): List<OracleHenvendelse> {
        return executeQuery(
            dataSource = henvendelseDb,
            query = "SELECT * FROM henvendelse WHERE henvendelse_id in (${paramlist(henvendelseIds.size)})",
            setVars = { stmt ->
                henvendelseIds.forEachIndexed { index, henvendelseId ->
                    stmt.setLong(index + 1, henvendelseId)
                }
            },
            process = { rs ->
                Row(rs).map { it.toHenvendelse() }.toList()
            }
        )
    }

    fun hentHendelser(henvendelseIds: List<Long>): Map<Long, List<OracleHendelse>> {
        return executeQuery(
            dataSource = henvendelseDb,
            query = "SELECT * FROM hendelse WHERE henvendelse_id in (${paramlist(henvendelseIds.size)})",
            setVars = { stmt ->
                henvendelseIds.forEachIndexed { index, henvendelseId ->
                    stmt.setLong(index + 1, henvendelseId)
                }
            },
            process = { rs ->
                Row(rs).map { it.toHendelse() }.toList()
            }
        ).groupBy { it.henvendelseId }
    }

    fun hentArkivposter(arkivpostIds: List<Long>): Map<Long, OracleArkivpost> {
        return executeQuery(
            dataSource = henvendelseArkivDb,
            query = "SELECT * FROM arkivpost WHERE arkivpostid in (${paramlist(arkivpostIds.size)})",
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

    fun hentVedlegg(arkivpostIds: List<Long>): Map<Long, OracleVedlegg> {
        return executeQuery(
            dataSource = henvendelseArkivDb,
            query = "SELECT * FROM vedlegg WHERE arkivpostid in (${paramlist(arkivpostIds.size)})",
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
}
