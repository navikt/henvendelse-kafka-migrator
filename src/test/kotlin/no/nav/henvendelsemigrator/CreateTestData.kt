package no.nav.henvendelsemigrator

import no.nav.henvendelsemigrator.domain.Arkivpost
import no.nav.henvendelsemigrator.domain.Hendelse
import no.nav.henvendelsemigrator.domain.Henvendelse
import no.nav.henvendelsemigrator.domain.Vedlegg
import no.nav.henvendelsemigrator.infrastructure.DataSourceConfiguration
import java.sql.PreparedStatement
import java.sql.Timestamp
import java.time.LocalDateTime
import java.util.*

data class Ingest(
    val henvendelse: Henvendelse,
    val hendelser: List<Hendelse>,
    val arkivpost: Arkivpost,
    val vedlegg: Vedlegg
)

fun main() {
    val config = LocalConfig()
    val henvendelseDb = DataSourceConfiguration.getDatasource(config.henvendelseDbConfig)
    val henvendelseArkivDb = DataSourceConfiguration.getDatasource(config.henvendelseArkivDbConfig)

    println(params(10))
    LongRange(1, 1_000_000).asSequence()
        .map { id ->
            Ingest(
                lagHenvendelse(id),
                lagHendelser(id),
                lagArkivpost(id),
                lagVedlegg(id),
            )
        }
        .chunked(1000)
        .forEach { ingestChunk ->
            val start = System.currentTimeMillis()
            val henvendelseCon = henvendelseDb.connection
            val arkivCon = henvendelseArkivDb.connection

            val henvendelseQuery = henvendelseCon.prepareStatement("INSERT INTO henvendelse VALUES (${params(26)})")
            val hendelseQuery = henvendelseCon.prepareStatement("INSERT INTO hendelse VALUES (${params(7)})")
            val arkivpostQuery = arkivCon.prepareStatement("INSERT INTO arkivpost VALUES (${params(20)})")
            val vedleggQuery = arkivCon.prepareStatement("INSERT INTO vedlegg VALUES (${params(8)})")

            for (ingest in ingestChunk) {
                henvendelseQuery.addHenvendelseToBatch(ingest.henvendelse)
                hendelseQuery.addHendelserToBatch(ingest.hendelser)
                arkivpostQuery.addArkivpostToBatch(ingest.arkivpost)
                vedleggQuery.addVedleggToBatch(ingest.vedlegg)
            }

            henvendelseQuery.executeBatch()
            hendelseQuery.executeBatch()
            arkivpostQuery.executeBatch()
            vedleggQuery.executeBatch()

            henvendelseCon.commit()
            arkivCon.commit()

            henvendelseQuery.close()
            hendelseQuery.close()
            arkivpostQuery.close()
            vedleggQuery.close()
            henvendelseCon.close()
            arkivCon.close()

            println("Commited chunk ending in ${ingestChunk.last().henvendelse.henvendelseId} (${System.currentTimeMillis() - start}ms)")
        }
}

fun PreparedStatement.addHenvendelseToBatch(henvendelse: Henvendelse) {
    setLong(1, henvendelse.henvendelseId)
    setString(2, henvendelse.behandlingsId)
    setString(3, henvendelse.behandlingsKjedeId)
    setString(4, henvendelse.type)
    setString(5, henvendelse.tema)
    setString(6, henvendelse.aktor)
    setString(7, henvendelse.status)
    setTimestamp(8, Timestamp.valueOf(henvendelse.opprettetdato))
    setTimestamp(9, Timestamp.valueOf(henvendelse.innsendtdato))
    setTimestamp(10, Timestamp.valueOf(henvendelse.sistendretdato))
    setString(11, henvendelse.behandlingsresultat)
    setString(12, henvendelse.journalfortSaksid)
    setString(13, henvendelse.journalfortTema)
    setString(14, henvendelse.journalportId)
    setString(15, henvendelse.batchStatus)
    setString(16, henvendelse.arkivpostId)
    setString(17, henvendelse.kontorsperre)
    setString(18, henvendelse.oppgaveIdGsak)
    setString(19, henvendelse.henvendelseIdGsak)
    setString(20, henvendelse.eksternAktor)
    setString(21, henvendelse.tilknyttetEnhet)
    setInt(22, henvendelse.erTilknyttetAnsatt.asNumeric())
    setString(23, henvendelse.brukersEnhet)
    setString(24, henvendelse.korrelasjonsId)
    setInt(25, henvendelse.oversendtDokmot.asNumeric())
    setString(26, henvendelse.behandlingstema)

    addBatch()
    clearParameters()
}

fun PreparedStatement.addHendelserToBatch(hendelser: List<Hendelse>) {
    for (hendelse in hendelser) {
        setLong(1, hendelse.id)
        setLong(2, hendelse.henvendelseId)
        setString(3, hendelse.aktor)
        setString(4, hendelse.type)
        setTimestamp(5, Timestamp.valueOf(hendelse.dato))
        setString(6, hendelse.enhet)
        setString(7, hendelse.verdi)

        addBatch()
        clearParameters()
    }
}

fun PreparedStatement.addArkivpostToBatch(arkivpost: Arkivpost) {
    setLong(1, arkivpost.arkivpostid)
    setTimestamp(2, Timestamp.valueOf(arkivpost.arkivertdato))
    setTimestamp(3, Timestamp.valueOf(arkivpost.mottattdato))
    setTimestamp(4, Timestamp.valueOf(arkivpost.utgaardato))
    setString(5, arkivpost.temagruppe)
    setString(6, arkivpost.arkivposttype)
    setString(7, arkivpost.dokumenttype)
    setString(8, arkivpost.kryssreferanseid)
    setString(9, arkivpost.kanal)
    setString(10, arkivpost.aktoerid)
    setString(11, arkivpost.fodselsnummer)
    setString(12, arkivpost.navident)
    setString(13, arkivpost.innhold)
    setString(14, arkivpost.journalfoerendeenhet)
    setString(15, arkivpost.status)
    setString(16, arkivpost.kategorikode)
    setInt(17, arkivpost.signert.asNumeric())
    setInt(18, arkivpost.erorganinternt.asNumeric())
    setInt(19, arkivpost.begrensetpartinnsyn.asNumeric())
    setInt(20, arkivpost.sensitiv.asNumeric())

    addBatch()
    clearParameters()
}

fun PreparedStatement.addVedleggToBatch(vedlegg: Vedlegg) {
    setLong(1, vedlegg.arkivpostid)
    setString(2, vedlegg.filnavn)
    setString(3, vedlegg.filtype)
    setString(4, vedlegg.variantformat)
    setString(5, vedlegg.tittel)
    setString(6, vedlegg.brevkode)
    setInt(7, vedlegg.strukturert.asNumeric())
    setString(8, vedlegg.dokument)

    addBatch()
    clearParameters()
}

fun params(l: Int) = "?"
    .repeat(l)
    .split("")
    .filter { it.isNotEmpty() }
    .joinToString(", ")

fun lagHenvendelse(henvendelseId: Long) = Henvendelse(
    henvendelseId = henvendelseId,
    behandlingsId = henvendelseId.toString(36),
    behandlingsKjedeId = henvendelseId.toString(36),
    type = "SPORSMAL_MODIA_UTGAAENDE",
    tema = "BID",
    aktor = "1000012345678",
    status = "FERDIG",
    opprettetdato = LocalDateTime.now().minusDays(1),
    innsendtdato = LocalDateTime.now().minusDays(1),
    sistendretdato = LocalDateTime.now().minusDays(1),
    behandlingsresultat = """
        <ns2:metadataListe>
            <metadata>
                <temagruppe>OKSOS</temagruppe>
                <fritekst>Hei, \n det er mye tekst som kan stå her. Dette er ID $henvendelseId (${henvendelseId.toString(36)})</fritekst>
            </metadata>
        </ns2:metadataListe>
    """.trimIndent(),
    journalfortSaksid = "123456asd",
    journalfortTema = "DAG",
    journalportId = "123456789",
    batchStatus = "LEDIG",
    arkivpostId = henvendelseId.toString(),
    kontorsperre = "1234",
    oppgaveIdGsak = "12345678",
    henvendelseIdGsak = "ABBA123456",
    eksternAktor = "0123456791",
    tilknyttetEnhet = "1234",
    erTilknyttetAnsatt = true,
    brukersEnhet = "123456",
    korrelasjonsId = UUID.nameUUIDFromBytes(henvendelseId.toString().toByteArray()).toString(),
    oversendtDokmot = false,
    behandlingstema = "KNA",
)

fun lagHendelser(henvendelseId: Long) = listOf(
    lagHendelse(0, henvendelseId),
    lagHendelse(1, henvendelseId),
    lagHendelse(2, henvendelseId),
    lagHendelse(3, henvendelseId)
)

fun lagHendelse(i: Int, henvendelseId: Long) = Hendelse(
    id = henvendelseId * 4 + i,
    henvendelseId = henvendelseId,
    aktor = "1000012345678",
    type = when (i) {
        0 -> "HENVENDELSE_OPPRETTET"
        1 -> "HENVENDELSE_AVSLUTTET"
        2 -> "HENVENDELSE_LEST"
        else -> "ENDRET_TEMAGRUPPE"
    },
    dato = LocalDateTime.now(),
    enhet = "1234",
    verdi = "OKSOK"
)

fun lagArkivpost(henvendelseId: Long) = Arkivpost(
    arkivpostid = henvendelseId,
    arkivertdato = LocalDateTime.now().minusDays(1),
    mottattdato = LocalDateTime.now().minusDays(1),
    utgaardato = LocalDateTime.now().plusDays(100),
    temagruppe = "OKSOS",
    arkivposttype = "UTGAAENDE",
    dokumenttype = "GEN_SVAR_001",
    kryssreferanseid = henvendelseId.toString(36),
    kanal = "NAV_NO",
    aktoerid = "1000012345678",
    fodselsnummer = "0123456791",
    navident = "Z999999",
    innhold = "ustrukturert tekst",
    journalfoerendeenhet = "JournalforendeEnhetRef",
    status = "ARKIVERT",
    kategorikode = "ED",
    signert = true,
    erorganinternt = false,
    begrensetpartinnsyn = false,
    sensitiv = false,
)

fun lagVedlegg(henvendelseId: Long) = Vedlegg(
    arkivpostid = henvendelseId,
    filnavn = "filnavn",
    filtype = "XML",
    variantformat = "ARKIV",
    tittel = "Spørsmål fra nav.no",
    brevkode = "9000021",
    strukturert = false,
    dokument = """
        <ns2:metadataListe>
            <metadata>
                <temagruppe>OKSOS</temagruppe>
                <fritekst>Hei, \n det er mye tekst som kan stå her. Dette er ID $henvendelseId (${henvendelseId.toString(36)})</fritekst>
            </metadata>
        </ns2:metadataListe>
    """.trimIndent(),
)

fun Boolean?.asNumeric() = if (this == true) 1 else 0
