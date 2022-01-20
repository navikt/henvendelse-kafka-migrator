package no.nav.henvendelsemigrator.introspect

import no.nav.henvendelsemigrator.Config
import no.nav.henvendelsemigrator.utils.kafka.KafkaUtils
import no.nav.henvendelsemigrator.utils.kafka.KafkaUtils.MissingOffsetStrategy
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.TopicPartition

object GetLastRecordOffset {
    data class Input(val topic: String)
    data class Output(val offset: Long)

    class Task(private val config: Config) : IntrospectionTask<Input, Output>(
        name = "Hent siste record offset",
        description = "Henter siste record offset for ett gitt topic: ${KafkaUtils.henvendelseTopic}, ${KafkaUtils.endringsloggTopic}",
        inputExample = Input(KafkaUtils.henvendelseTopic)
    ) {
        override fun action(input: Input): Output {
            val kafkaConsumer = KafkaConsumer<String, String>(KafkaUtils.consumerConfig(null, null, MissingOffsetStrategy.START_AT_BEGINNING,  config))
            return kafkaConsumer.use { consumer ->
                val topicPartition = TopicPartition(input.topic, 0)
                consumer.assign(listOf(topicPartition))
                consumer.seekToEnd(listOf(topicPartition))
                Output(consumer.position(topicPartition))
            }
        }
    }
}
