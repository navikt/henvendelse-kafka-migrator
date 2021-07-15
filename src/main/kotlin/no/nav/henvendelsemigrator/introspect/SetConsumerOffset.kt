package no.nav.henvendelsemigrator.introspect

import no.nav.henvendelsemigrator.Config
import no.nav.henvendelsemigrator.utils.kafka.KafkaUtils
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.TopicPartition

object SetConsumerOffset {
    data class Input(val topic: String, val offset: Long)
    data class Output(val offset: Long)

    class Task(private val config: Config) : IntrospectionTask<Input, Output>(
        name = "Sett consumer offset",
        description = "Sett consumer offset for ett gitt topic: ${KafkaUtils.endringsloggTopic}",
        inputExample = Input(KafkaUtils.henvendelseTopic, 0)
    ) {
        override fun action(input: Input): Output {
            val kafkaConsumer = KafkaConsumer<String, String>(KafkaUtils.consumerConfig(KafkaUtils.consumerGroupId, null, config))
            return kafkaConsumer.use { consumer ->
                val topicPartition = TopicPartition(input.topic, 0)
                consumer.assign(listOf(topicPartition))
                consumer.seek(topicPartition, input.offset)
                consumer.commitSync()
                Output(consumer.position(topicPartition))
            }
        }
    }
}
