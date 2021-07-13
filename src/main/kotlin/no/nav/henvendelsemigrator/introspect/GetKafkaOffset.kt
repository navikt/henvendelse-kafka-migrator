package no.nav.henvendelsemigrator.introspect

import no.nav.henvendelsemigrator.Config
import no.nav.henvendelsemigrator.utils.kafka.KafkaUtils
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.TopicPartition

data class GetKafkaOffsetRequest(val topic: String)
data class GetKafkaOffsetResponse(val offset: Long)

class GetKafkaOffset(private val config: Config) : IntrospectionTask<GetKafkaOffsetRequest, GetKafkaOffsetResponse>(
    name = "GetKafkaOffset",
    description = "Gets the last offset from the kafka topic",
    inputExample = GetKafkaOffsetRequest(KafkaUtils.henvendelseTopic)
) {
    override fun action(input: GetKafkaOffsetRequest): GetKafkaOffsetResponse {
        val kafkaConsumer = KafkaConsumer<String, String>(KafkaUtils.consumerConfig(null, null, config))
        return kafkaConsumer.use { consumer ->
            val topicPartition = TopicPartition(input.topic, 0)
            consumer.assign(listOf(topicPartition))
            consumer.seekToEnd(listOf(topicPartition))
            GetKafkaOffsetResponse(consumer.position(topicPartition))
        }
    }
}
