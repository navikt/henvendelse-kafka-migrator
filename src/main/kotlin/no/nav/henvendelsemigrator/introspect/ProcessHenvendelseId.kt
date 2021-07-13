package no.nav.henvendelsemigrator.introspect

import no.nav.henvendelsemigrator.domain.Henvendelse
import no.nav.henvendelsemigrator.tasks.ProcessChangesTask

data class HenvendelseId(val henvendelseId: Long)
class ProcessHenvendelseId(private val processingTask: ProcessChangesTask) : IntrospectionTask<HenvendelseId, List<Henvendelse>>(
    name = "DebugHenvendelseId",
    description = "Leser ut og prosesserer henvendelse_id for å se hva som ville blitt lagt til kafka",
    inputExample = HenvendelseId(123)
) {
    override fun action(input: HenvendelseId): List<Henvendelse> {
        return processingTask.processChunk(listOf(input.henvendelseId))
    }
}
