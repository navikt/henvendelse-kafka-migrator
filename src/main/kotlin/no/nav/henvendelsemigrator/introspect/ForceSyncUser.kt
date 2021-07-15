package no.nav.henvendelsemigrator.introspect

import kotliquery.Row
import no.nav.henvendelsemigrator.domain.HenvendelseType
import no.nav.henvendelsemigrator.infrastructure.HealthcheckedDataSource
import no.nav.henvendelsemigrator.tasks.ProcessChangesTask
import no.nav.henvendelsemigrator.utils.executeQuery
import no.nav.henvendelsemigrator.utils.kafka.KafkaUtils
import no.nav.henvendelsemigrator.utils.toJson
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord

object ForceSyncUser {
    data class Input(val fnr: String)
    data class Output(val antall: Int, val prosessert: List<Long>)

    class Task(
        val henvendelseDb: HealthcheckedDataSource,
        val processor: ProcessChangesTask,
        val kafka: KafkaProducer<String, String>
    ) : IntrospectionTask<Input, Output>(
        name = "Synkroniser bruker",
        description = "Henter alle henvendelser for bruker, prosesserer disse og legger de på kafka",
        inputExample = Input("10108000398") // Aremark
    ) {
        private val henvendelsetyper = HenvendelseType
            .values()
            .joinToString(", ") { "'$it'" }

        override fun action(input: Input): Output {
            val aktorId: String = executeQuery(
                dataSource = henvendelseDb,
                query = "SELECT aktorid from aktor_fnr_mapping where fnr = ?",
                setVars = { stmt -> stmt.setString(1, input.fnr) },
                process = { rs ->
                    Row(rs).map { it.string("aktorid") }.single()
                }
            )
            val henvendelseIder: List<Long> = executeQuery(
                dataSource = henvendelseDb,
                query = "SELECT henvendelse_id FROM henvendelse where aktor = ? AND type in ($henvendelsetyper) AND status = 'FERDIG'",
                setVars = { stmt -> stmt.setString(1, aktorId) },
                process = { rs ->
                    Row(rs)
                        .map { it.string("henvendelse_id").toLong() }
                        .distinct()
                        .toList()
                }
            )
            henvendelseIder
                .chunked(1000)
                .flatMap { processor.processChunk(it) }
                .forEach {
                    kafka.send(
                        ProducerRecord(KafkaUtils.henvendelseTopic, it.henvendelseId.toString(), it.toJson())
                    )
                }

            return Output(antall = henvendelseIder.size, prosessert = henvendelseIder)
        }
    }
}
