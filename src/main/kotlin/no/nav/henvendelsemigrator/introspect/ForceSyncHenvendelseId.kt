package no.nav.henvendelsemigrator.introspect

import no.nav.henvendelsemigrator.tasks.ProcessChangesTask
import no.nav.henvendelsemigrator.utils.kafka.KafkaUtils
import no.nav.henvendelsemigrator.utils.toJson
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord

object ForceSyncHenvendelseId {
    data class Input(val henvendelseIder: List<Long>)
    data class Output(val antall: Int, val prosessert: List<Long>)

    class Task(
        private val processor: ProcessChangesTask,
        private val kafka: KafkaProducer<String, String>
    ) : IntrospectionTask<Input, Output>(
        name = "Synkroniser henvendelser",
        description = "Oppdater henvendelser på kafka",
        inputExample = Input(listOf(123456L, 456789L))
    ) {
        override fun action(input: Input): Output {
            input.henvendelseIder
                .chunked(1000)
                .flatMap { processor.processChunk(it) }
                .forEach {
                    kafka.send(
                        ProducerRecord(KafkaUtils.henvendelseTopic, it.henvendelseId.toString(), it.toJson())
                    )
                }

            return Output(antall = input.henvendelseIder.size, prosessert = input.henvendelseIder)
        }
    }
}
