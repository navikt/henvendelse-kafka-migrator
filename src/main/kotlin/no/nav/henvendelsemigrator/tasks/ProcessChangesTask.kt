package no.nav.henvendelsemigrator.tasks

import kotlinx.coroutines.*
import kotliquery.Row
import no.nav.henvendelsemigrator.domain.*
import no.nav.henvendelsemigrator.infrastructure.HealthcheckedDataSource
import no.nav.henvendelsemigrator.infrastructure.health.Healthcheck
import no.nav.henvendelsemigrator.infrastructure.health.HealthcheckResult
import no.nav.henvendelsemigrator.log
import no.nav.henvendelsemigrator.utils.XMLParser
import no.nav.henvendelsemigrator.utils.executeQuery
import no.nav.henvendelsemigrator.utils.kafka.KafkaUtils
import no.nav.henvendelsemigrator.utils.paramlist
import no.nav.henvendelsemigrator.utils.toJson
import org.apache.kafka.clients.consumer.ConsumerRecords
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

class ProcessChangesTask(
    autoStart: Boolean,
    private val consumer: KafkaConsumer<String, String>,
    private val producer: KafkaProducer<String, String>,
    private val henvendelseDb: HealthcheckedDataSource,
    private val henvendelseArkivDb: HealthcheckedDataSource,
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

    init {
        if (autoStart) {
            runBlocking {
                start()
            }
        }
    }

    override suspend fun start() {
        if (process != null) throw IllegalStateException("Task $name is already running")
        withContext(Dispatchers.IO) {
            log.info("Starting $name ${LocalDateTime.now()}")
            startingTime = LocalDateTime.now()
            endTime = null
            process = GlobalScope.launch {
                consumer.subscribe(listOf(KafkaUtils.endringsloggTopic))
                while (isRunning()) {
                    val records: ConsumerRecords<String, String> = consumer.poll(Duration.ofMillis(10_000))
                    log.info("Polled records from kafka, got ${records.count()} record(s).")
                    val start = System.currentTimeMillis()
                    process(records)
                    consumer.commitSync()
                    log.info("Processed ${records.count()} records in ${System.currentTimeMillis() - start}ms")
                    processed += records.count()
                }
                consumer.unsubscribe()
                log.info("Stopped task $name")
            }
            log.info("Started $name ${LocalDateTime.now()}")
        }
    }

    override suspend fun stop() {
        if (process == null) throw IllegalStateException("Task $name is not running")
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

    override fun toHealtchCheck() = Healthcheck {
        val start = System.currentTimeMillis()
        try {
            executeQuery(
                henvendelseDb, "SELECT henvendelse_id FROM HENVENDELSE WHERE behandlingsid = ? OR behandlingsid = ?",
                {
                    it.setString(1, "11pg") // LocalTest
                    it.setString(2, "1000C4YTW") // OracleTest
                },
                { rs ->
                    var description: String? = null
                    for (it in Row(rs)) {
                        val henvendelseId = it.string("henvendelse_id").toLong()
                        val henvendelser = hentHenvendelser(listOf(henvendelseId))
                        val arkivpostIds: List<Long> = henvendelser.mapNotNull { it.arkivpostId?.toLong() }
                        val arkivposter: Map<Long, OracleArkivpost> = hentArkivposter(arkivpostIds)
                        val vedlegg: Map<Long, OracleVedlegg> = hentVedlegg(arkivpostIds)
                        val hendelser: Map<Long, List<OracleHendelse>> = hentHendelser(listOf(henvendelseId))
                        val sammenslatt = processHenvendelse(
                            henvendelse = henvendelser.first(),
                            hendelser = hendelser[henvendelser.firstOrNull()?.henvendelseId] ?: emptyList(),
                            arkivpost = arkivpostIds.firstOrNull()?.let { arkivposter[it] },
                            vedlegg = arkivpostIds.firstOrNull()?.let { vedlegg[it] },
                            fallbackFnrFraHenvendelseMapping = null
                        )
                        description = sammenslatt.toJson()
                    }
                    if (description == null) {
                        HealthcheckResult.Error(
                            name,
                            System.currentTimeMillis() - start,
                            IllegalStateException("No matching rows found")
                        )
                    } else {
                        HealthcheckResult.Ok(name, System.currentTimeMillis() - start, description)
                    }
                }
            )
        } catch (throwable: Throwable) {
            HealthcheckResult.Error(name, System.currentTimeMillis() - start, throwable)
        }
    }

    private fun process(records: ConsumerRecords<String, String>) {
        records
            .map { it.value().toLong() }
            .chunked(1000)
            .flatMap(::processChunk)
            .forEach {
                producer.send(ProducerRecord(KafkaUtils.henvendelseTopic, it.henvendelseId.toString(), it.toJson()))
            }
    }

    fun processChunk(henvendelseIds: List<Long>): List<Henvendelse> {
        val henvendelser: List<OracleHenvendelse> = hentHenvendelser(henvendelseIds)
        val aktorIdHvorFnrMappingIkkeKanHentesFraArkivpost = henvendelser
            .filter { it.arkivpostId == null }
            .mapNotNull { it.aktor }
        val aktorFnrMapping: Map<String, String> = hentAktorFnrMapping(aktorIdHvorFnrMappingIkkeKanHentesFraArkivpost)
        val arkivpostIds: List<Long> = henvendelser.mapNotNull { it.arkivpostId?.toLong() }
        val hendelser: Map<Long, List<OracleHendelse>> = hentHendelser(henvendelseIds)
        val arkivposter: Map<Long, OracleArkivpost> = hentArkivposter(arkivpostIds)
        val vedlegg: Map<Long, OracleVedlegg> = hentVedlegg(arkivpostIds)

        return henvendelser
            .map { henvendelse ->
                val arkivpostId = henvendelse.arkivpostId?.toLong()
                processHenvendelse(
                    henvendelse = henvendelse,
                    hendelser = hendelser[henvendelse.henvendelseId] ?: emptyList(),
                    arkivpost = arkivpostId?.let { arkivposter[it] },
                    vedlegg = arkivpostId?.let { vedlegg[it] },
                    fallbackFnrFraHenvendelseMapping = aktorFnrMapping[henvendelse.aktor]
                )
            }
    }

    private fun processHenvendelse(
        henvendelse: OracleHenvendelse,
        hendelser: List<OracleHendelse>,
        arkivpost: OracleArkivpost?,
        vedlegg: OracleVedlegg?,
        fallbackFnrFraHenvendelseMapping: String?
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
        val temagruppe: Temagruppe? = (
            hendelsemap[HendelseKeys.endretTemagruppe]?.lastOrNull()?.verdi
                ?: melding?.temagruppe?.name
            )?.let { Temagruppe.valueOf(it) }

        val utgaarDato: ZonedDateTime = (arkivpost?.utgaardato ?: finnUtgaarDatoGittTemagruppe(temagruppe))
            .atZone(ZoneId.systemDefault())

        return Henvendelse(
            henvendelseId = henvendelse.henvendelseId,
            behandlingsId = henvendelse.behandlingsId,
            behandlingskjedeId = henvendelse.behandlingsKjedeId,
            applikasjonsId = null, // Settes aldri av henvendelse
            fnr = arkivpost?.fodselsnummer ?: fallbackFnrFraHenvendelseMapping,
            aktorId = henvendelse.aktor,
            tema = henvendelse.tema,
            behandlingstema = henvendelse.behandlingstema,
            ferdigstiltUtenSvar = ferdigstiltUtenSvarHendelse != null,
            henvendelseType = HenvendelseType.valueOf(requireNotNull(henvendelse.type)),
            eksternAktor = henvendelse.eksternAktor,
            tilknyttetEnhet = henvendelse.tilknyttetEnhet,
            opprettetDato = henvendelse.opprettetdato.atZone(ZoneId.systemDefault()),
            avsluttetDato = henvendelse.innsendtdato?.atZone(ZoneId.systemDefault()),
            utgaarDato = utgaarDato,
            lestDato = hendelsemap[HendelseKeys.lest]?.firstOrNull()?.dato?.atZone(ZoneId.systemDefault()),
            kontorsperreEnhet = henvendelse.kontorsperre,
            brukersEnhet = henvendelse.brukersEnhet,
            markertSomFeilsendtAv = feilsendtHendelse?.aktor,
            oppgaveIdGsak = henvendelse.oppgaveIdGsak,
            henvendelseIdGsak = henvendelse.henvendelseIdGsak,
            erTilknyttetAnsatt = henvendelse.erTilknyttetAnsatt,
            gjeldendeTemagruppe = temagruppe,
            journalfortInformasjon = henvendelse.journalpostId?.let {
                JournalfortInformasjon(
                    journalpostId = henvendelse.journalpostId,
                    journalfortTema = henvendelse.journalfortTema,
                    journalfortDato = tilknyttning?.dato?.atZone(ZoneId.systemDefault()),
                    journalfortSaksId = henvendelse.journalfortSaksid,
                    journalforerNavIdent = tilknyttning?.aktor,
                    journalforendeEnhet = tilknyttning?.enhet
                )
            },
            markeringer = Markeringer(
                kontorsperre = kontorsperre,
                feilsendt = feilsendt,
                ferdigstiltUtenSvar = ferdigstiltUtenSvar
            ),
            korrelasjonsId = henvendelse.korrelasjonsId,
            metadataListe = if (melding == null) null else {
                MetadataListe(listOf(melding))
            }
        )
    }

    fun lagMelding(henvendelse: OracleHenvendelse, arkivpost: OracleArkivpost?, vedlegg: OracleVedlegg?): Melding? {
        if (arkivpost?.status == "KASSERT") {
            return null
        }
        val content: String = if (henvendelse.behandlingsresultat != null) {
            henvendelse.behandlingsresultat
        } else if (vedlegg?.dokument != null) {
            String(vedlegg.dokument)
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

    private fun hentAktorFnrMapping(aktorIds: List<String>): Map<String, String> {
        if (aktorIds.isEmpty())return emptyMap()
        return executeQuery(
            dataSource = henvendelseArkivDb,
            query = "SELECT aktorid, fnr FROM aktor_fnr_mapping where aktorid in (${paramlist(aktorIds.size)})",
            setVars = { stmt ->
                aktorIds.forEachIndexed { index, aktorId -> stmt.setString(index + 1, aktorId) }
            },
            process = { rs ->
                Row(rs)
                    .map { it.string("aktorid") to it.string("fnr") }
                    .toMap()
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
        ).sortedBy { it.dato }
            .groupBy { it.henvendelseId }
    }

    fun hentArkivposter(arkivpostIds: List<Long>): Map<Long, OracleArkivpost> {
        if (arkivpostIds.isEmpty()) return emptyMap()
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
        if (arkivpostIds.isEmpty()) return emptyMap()
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

    private fun finnUtgaarDatoGittTemagruppe(temagruppe: Temagruppe?): LocalDateTime {
        return if (listOf(Temagruppe.ANSOS, Temagruppe.OKSOS).contains(temagruppe))
            LocalDateTime.now().minusYears(2)
        else
            LocalDateTime.now().minusYears(25)
    }
}
