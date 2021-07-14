package no.nav.henvendelsemigrator.introspect

import kotliquery.using
import no.nav.henvendelsemigrator.infrastructure.HealthcheckedDataSource
import no.nav.henvendelsemigrator.tasks.SIST_PROSESSERT_HENDELSE

object SetLastProcessedHendelse {
    data class Input(val id: Long)
    data class Output(val id: Long)

    class Task(private val henvendelseDb: HealthcheckedDataSource) : IntrospectionTask<Input, Output>(
        name = "Sett siste prosesserte hendelse",
        description = "Setter sist prosesserte henvendelse i migreringmetadatatabell",
        inputExample = Input(123456789)
    ) {
        override fun action(input: Input): Output {
            using(henvendelseDb.getOrThrow().connection) { connection ->
                val stmt = connection
                    .prepareStatement("UPDATE migreringmetadata SET value = ? WHERE key = '$SIST_PROSESSERT_HENDELSE'")
                stmt.setString(1, input.id.toString())
                stmt.execute()
                connection.commit()
            }
            return Output(input.id)
        }
    }
}
