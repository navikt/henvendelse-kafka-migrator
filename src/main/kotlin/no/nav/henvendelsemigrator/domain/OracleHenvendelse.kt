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
    val journalportId: String? = null,
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
    journalportId = this.stringOrNull("journalpostid"),
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

object HendelseKeys {
    const val ferdigstiltUtenSvar = "HENVENDELSE_FERDIGSTILT_UTEN_SVAR"
    const val lest = "HENVENDELSE_LEST"
    const val feilsendt = "MARKERT_SOM_FEILSENDT"
    const val kontorsperret = "KONTORSPERRET"

    const val endretTemagruppe = "ENDRET_TEMAGRUPPE"
    const val knyttetTilTema = "KNYTTET_TIL_TEMA"
    const val knyttetTilSak = "KNYTTET_TIL_SAK"
}
