package no.nav.henvendelsemigrator.domain

import kotliquery.Row
import java.time.LocalDateTime

data class OracleHenvendelse(
    val henvendelseId: Long,
    val behandlingsId: String,
    val behandlingsKjedeId: String,
    val type: String? = null,
    val tema: String? = null,
    val aktor: String? = null,
    val status: String? = null,
    val opprettetdato: LocalDateTime,
    val innsendtdato: LocalDateTime? = null,
    val sistendretdato: LocalDateTime? = null,
    val behandlingsresultat: String? = null,
    val journalfortSaksid: String? = null,
    val journalfortTema: String? = null,
    val journalpostId: String? = null,
    val batchStatus: String? = null,
    val arkivpostId: String? = null,
    val kontorsperre: String? = null,
    val oppgaveIdGsak: String? = null,
    val henvendelseIdGsak: String? = null,
    val eksternAktor: String? = null,
    val tilknyttetEnhet: String? = null,
    val erTilknyttetAnsatt: Boolean? = null,
    val brukersEnhet: String? = null,
    val korrelasjonsId: String? = null,
    val oversendtDokmot: Boolean? = null,
    val behandlingstema: String? = null
)
fun Row.toHenvendelse() = OracleHenvendelse(
    henvendelseId = this.long("henvendelse_id"),
    behandlingsId = this.string("behandlingsid"),
    behandlingsKjedeId = this.string("behandlingskjedeid"),
    type = this.stringOrNull("type"),
    tema = this.stringOrNull("tema"),
    aktor = this.stringOrNull("aktor"),
    status = this.stringOrNull("status"),
    opprettetdato = this.localDateTime("opprettetdato"),
    innsendtdato = this.localDateTimeOrNull("innsendtdato"),
    sistendretdato = this.localDateTimeOrNull("sistendretdato"),
    behandlingsresultat = this.stringOrNull("behandlingsresultat"),
    journalfortSaksid = this.stringOrNull("journalfortsaksid"),
    journalfortTema = this.stringOrNull("journalforttema"),
    journalpostId = this.stringOrNull("journalpostid"),
    batchStatus = this.stringOrNull("batch_status"),
    arkivpostId = this.stringOrNull("arkivpostid"),
    kontorsperre = this.stringOrNull("kontorsperre"),
    oppgaveIdGsak = this.stringOrNull("oppgaveidgsak"),
    henvendelseIdGsak = this.stringOrNull("henvendelseidgsak"),
    eksternAktor = this.stringOrNull("eksternaktor"),
    tilknyttetEnhet = this.stringOrNull("tilknyttetenhet"),
    erTilknyttetAnsatt = this.boolean("ertilknyttetansatt"),
    brukersEnhet = this.stringOrNull("brukersenhet"),
    korrelasjonsId = this.stringOrNull("korrelasjonsid"),
    oversendtDokmot = this.boolean("oversendtdokmot"),
    behandlingstema = this.stringOrNull("behandlingstema")
)
data class OracleHendelse(
    val id: Long,
    val henvendelseId: Long,
    val aktor: String? = null,
    val type: String? = null,
    val dato: LocalDateTime? = null,
    val enhet: String? = null,
    val verdi: String? = null
)
fun Row.toHendelse() = OracleHendelse(
    id = this.long("id"),
    henvendelseId = this.long("henvendelse_id"),
    aktor = this.string("aktor"),
    type = this.string("type"),
    dato = this.localDateTime("dato"),
    enhet = this.stringOrNull("enhet"),
    verdi = this.stringOrNull("verdi")
)
enum class HendelseType {
    HENVENDELSE_AVSLUTTET,
    HENVENDELSE_FERDIGSTILT_UTEN_SVAR,
    HENVENDELSE_LEST,
    HENVENDELSE_SLATT_SAMMEN,
    HENVENDELSE_IKKE_LEST_INNEN_FRIST,
    HENVENDELSE_IKKE_BESVART_INNEN_FRIST,
    HENVENDELSE_BESVART,
    OVERSENDT_JOARK,
    OVERSENDT_HENVENDESLSESARKIV,
    GSAK_OPPGAVE_OPPRETTET,
    GSAK_HENVENDELSE_OPPRETTET,
    KONTORSPERRET,
    MARKERT_SOM_FEILSENDT,
    MARKERT_SOM_HASTEKASSERING,
    KNYTTET_TIL_SAK,
    KNYTTET_TIL_TEMA,
    ENDRET_TEMAGRUPPE;
//  Hendelser som ikke vil føre til oppdatering.
//    HENVENDELSE_OPPRETTET,
//    HENVENDELSE_AVBRUTT,
//    HENVENDELSE_AUTOMATISK_AVBRUTT,
//    NOTIFIKASJON_SENDT,
//    OVERSENDT_DOKMOT,
//    HENVENDELSE_SLETTET_JOURNALPOST,
//    HENVENDELSE_SLETTET_VEDLEGG,
//    SKJEMA_VALGT,
//    FAKTA_REGISTRERT,
//    SIGNERT_OG_SENDT_INN,
}

object HendelseKeys {
    val ferdigstiltUtenSvar = HendelseType.HENVENDELSE_FERDIGSTILT_UTEN_SVAR.name
    val lest = HendelseType.HENVENDELSE_LEST.name
    val feilsendt = HendelseType.MARKERT_SOM_FEILSENDT.name
    val kontorsperret = HendelseType.KONTORSPERRET.name

    val endretTemagruppe = HendelseType.ENDRET_TEMAGRUPPE.name
    val knyttetTilTema = HendelseType.KNYTTET_TIL_TEMA.name
    val knyttetTilSak = HendelseType.KNYTTET_TIL_SAK.name
}
