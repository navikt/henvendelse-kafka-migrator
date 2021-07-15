package no.nav.henvendelsemigrator.introspect

import no.nav.henvendelsemigrator.Config
import no.nav.henvendelsemigrator.utils.kafka.KafkaUtils
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.TopicPartition

object GetConsumerOffset {
    data class Input(val topic: String)
    data class Output(val committed: Map<String, Long>)

    class Task(private val config: Config) : IntrospectionTask<Input, Output>(
        name = "Hent consumer offset",
        description = "Henter consumer offset for ett gitt topic: ${KafkaUtils.endringsloggTopic}",
        inputExample = Input(KafkaUtils.endringsloggTopic)
    ) {
        override fun action(input: Input): Output {
            val kafkaConsumer = KafkaConsumer<String, String>(KafkaUtils.consumerConfig(KafkaUtils.consumerGroupId, null, config))
            return kafkaConsumer.use { consumer ->
                val topicPartitions = consumer.partitionsFor(input.topic)
                    .map { TopicPartition(it.topic(), it.partition()) }
                    .toSet()
                val committed: Map<String, Long> = consumer
                    .committed(topicPartitions)
                    .filterValues { it != null }
                    .mapKeys { "${it.key.topic()}[${it.key.partition()}]" }
                    .mapValues { it.value.offset() }
                Output(committed)
            }
        }
    }
}
