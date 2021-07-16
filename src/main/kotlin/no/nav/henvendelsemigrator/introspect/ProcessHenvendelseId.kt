package no.nav.henvendelsemigrator.introspect

import no.nav.henvendelsemigrator.domain.Henvendelse
import no.nav.henvendelsemigrator.tasks.ProcessChangesTask

object ProcessHenvendelseId {
    data class Input(val henvendelseId: Long)

    class Task(private val processingTask: ProcessChangesTask) : IntrospectionTask<Input, List<Henvendelse>>(
        name = "Dry-run prosessering av enkelt henvendelse",
        description = "Leser ut og prosesserer henvendelse_id for å se hva som ville blitt lagt til kafka",
        inputExample = Input(123)
    ) {
        override fun action(input: Input): List<Henvendelse> {
            return processingTask.processChunk(listOf(input.henvendelseId))
        }
    }
}
