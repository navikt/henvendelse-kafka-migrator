package no.nav.henvendelsemigrator.domain

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.ZonedDateTime

enum class Temagruppe {
    ARBD,
    HELSE,
    FMLI,
    FDAG,
    HJLPM,
    BIL,
    ORT_HJE,
    OVRG,
    PENS,
    PLEIEPENGERSY,
    UFRT,
    UTLAND,
    OKSOS,
    ANSOS;
}
enum class Kanal {
    TEKST, TELEFON, OPPMOTE
}

enum class HenvendelseType {
    // Fjerner typer som ikke skal oversendes til SF
    SPORSMAL_SKRIFTLIG,
    SPORSMAL_SKRIFTLIG_DIREKTE,
    SVAR_SKRIFTLIG,
    SVAR_OPPMOTE,
    SVAR_TELEFON,
    DELVIS_SVAR_SKRIFTLIG,
    REFERAT_OPPMOTE,
    REFERAT_TELEFON,
    SPORSMAL_MODIA_UTGAAENDE,
    INFOMELDING_MODIA_UTGAAENDE,
    SVAR_SBL_INNGAAENDE
}

class Henvendelse(
    val henvendelseId: Long,
    val behandlingsId: String,
    val behandlingskjedeId: String,
    val applikasjonsId: String?,
    val fnr: String?,
    val aktorId: String?,
    val tema: String?,
    val behandlingstema: String?,
    val ferdigstiltUtenSvar: Boolean?,
    val henvendelseType: HenvendelseType?,
    val eksternAktor: String?,
    val tilknyttetEnhet: String?,
    val opprettetDato: ZonedDateTime?,
    val avsluttetDato: ZonedDateTime?,
    val lestDato: ZonedDateTime?,
    val kontorsperreEnhet: String?,
    val brukersEnhet: String?,
    val markertSomFeilsendtAv: String?,
    val oppgaveIdGsak: String?,
    val henvendelseIdGsak: String?,
    val erTilknyttetAnsatt: Boolean?,
    val gjeldendeTemagruppe: Temagruppe?,
    val journalfortInformasjon: JournalfortInformasjon?,
    val markeringer: Markeringer?,
    val korrelasjonsId: String?,
    val metadataListe: MetadataListe?
) {
    @JsonProperty("KAFKA_MESSAGE_VERSION")
    private val KAFKA_MESSAGE_VERSION: String = "1.0"
}

class JournalfortInformasjon(
    val journalpostId: String?,
    val journalfortTema: String?,
    val journalfortDato: ZonedDateTime?,
    val journalfortSaksId: String?,
    val journalforerNavIdent: String?
)

class Markeringer(
    val kontorsperre: Kontorsperre?,
    val feilsendt: Markering?,
    val ferdigstiltUtenSvar: Markering?
)
open class Markering(
    val dato: ZonedDateTime?,
    val aktor: String?
)
class Kontorsperre(
    dato: ZonedDateTime?,
    aktor: String?,
    val enhet: String?
) : Markering(dato, aktor)

class MetadataListe(
    val metadata: List<Melding>?
)

abstract class Melding(
    val temagruppe: Temagruppe?,
    val fritekst: String?
)

class MeldingFraBruker(
    temagruppe: Temagruppe?,
    fritekst: String?,
) : Melding(temagruppe, fritekst)

class MeldingTilBruker(
    temagruppe: Temagruppe?,
    fritekst: String?,
    val sporsmalsId: String?,
    val kanal: Kanal?,
    val navident: String?
) : Melding(temagruppe, fritekst)
