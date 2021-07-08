package no.nav.henvendelsemigrator.domain

import kotliquery.Row
import java.time.LocalDateTime

data class Arkivpost(
    val arkivpostid: Long,
    val arkivertdato: LocalDateTime? = null,
    val mottattdato: LocalDateTime? = null,
    val utgaardato: LocalDateTime? = null,
    val temagruppe: String? = null,
    val arkivposttype: String? = null,
    val dokumenttype: String? = null,
    val kryssreferanseid: String? = null,
    val kanal: String? = null,
    val aktoerid: String? = null,
    val fodselsnummer: String? = null,
    val navident: String? = null,
    val innhold: String? = null,
    val journalfoerendeenhet: String? = null,
    val status: String? = null,
    val kategorikode: String? = null,
    val signert: Boolean? = null,
    val erorganinternt: Boolean? = null,
    val begrensetpartinnsyn: Boolean? = null,
    val sensitiv: Boolean? = null
)
fun Row.toArkivpost() = Arkivpost(
    arkivpostid = this.long("arkivpostId"),
    arkivertdato = this.localDateTimeOrNull("arkivertDato"),
    mottattdato = this.localDateTimeOrNull("mottattDato"),
    utgaardato = this.localDateTimeOrNull("utgaarDato"),
    temagruppe = this.stringOrNull("temagruppe"),
    arkivposttype = this.stringOrNull("arkivpostType"),
    dokumenttype = this.stringOrNull("dokumentType"),
    kryssreferanseid = this.stringOrNull("kryssreferanseId"),
    kanal = this.stringOrNull("kanal"),
    aktoerid = this.stringOrNull("aktoerId"),
    fodselsnummer = this.stringOrNull("fodselsnummer"),
    navident = this.stringOrNull("navIdent"),
    innhold = this.stringOrNull("innhold"),
    journalfoerendeenhet = this.stringOrNull("journalfoerendeEnhet"),
    status = this.stringOrNull("status"),
    kategorikode = this.stringOrNull("kategorikode"),
    signert = this.boolean("signert"),
    erorganinternt = this.boolean("erOrganInternt"),
    begrensetpartinnsyn = this.boolean("begrensetPartInnsyn"),
    sensitiv = this.boolean("sensitiv")
)

data class Vedlegg(
    val arkivpostid: Long,
    val filnavn: String? = null,
    val filtype: String? = null,
    val variantformat: String? = null,
    val tittel: String? = null,
    val brevkode: String? = null,
    val strukturert: Boolean? = null,
    val dokument: String? = null
)
fun Row.toVedlegg() = Vedlegg(
    arkivpostid = this.long("arkivpostid"),
    filnavn = this.stringOrNull("filnavn"),
    filtype = this.stringOrNull("filtype"),
    variantformat = this.stringOrNull("variantformat"),
    tittel = this.stringOrNull("tittel"),
    brevkode = this.stringOrNull("brevkode"),
    strukturert = this.boolean("strukturert"),
    dokument = this.stringOrNull("dokument"),
)