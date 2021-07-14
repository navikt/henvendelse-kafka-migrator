package no.nav.henvendelsemigrator.introspect

import no.nav.henvendelsemigrator.Config
import no.nav.henvendelsemigrator.utils.fromJson
import no.nav.henvendelsemigrator.utils.kafka.KafkaUtils
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.TopicPartition
import java.time.Duration

object ReadKafkaTopic {
    data class KafkaRecord(val timestamp: Long, val offset: Long, val key: String, val value: Any)
    data class Input(
        val topic: String,
        val maxRecords: Int,
        val fromOffset: Long
    )

    class Task(private val config: Config) : IntrospectionTask<Input, List<KafkaRecord>>(
        name = "Les meldinger fra kafka",
        description = "Leser meldinger fra kafka topics: ${KafkaUtils.henvendelseTopic}, ${KafkaUtils.endringsloggTopic}",
        inputExample = Input(KafkaUtils.henvendelseTopic, 10, 100)
    ) {
        override fun action(input: Input): List<KafkaRecord> {
            val kafkaConsumer = KafkaConsumer<String, String>(KafkaUtils.consumerConfig(null, null, config))
            return kafkaConsumer.use { consumer ->
                val records = mutableListOf<KafkaRecord>()
                val topicPartition = TopicPartition(KafkaUtils.henvendelseTopic, 0)
                val fromOffset = java.lang.Long.max(0, input.fromOffset)

                consumer.assign(listOf(topicPartition))
                consumer.seek(topicPartition, fromOffset)

                while (records.size < input.maxRecords) {
                    val consumerRecords = consumer.poll(Duration.ofMillis(1_000))
                    if (consumerRecords.isEmpty) {
                        break
                    }
                    records.addAll(
                        consumerRecords.map {
                            KafkaRecord(
                                timestamp = it.timestamp(),
                                offset = it.offset(),
                                key = it.key(),
                                value = it.value().fromJson<Map<String, Any?>>()
                            )
                        }
                    )
                }
                if (records.size > input.maxRecords) {
                    records.subList(0, input.maxRecords)
                } else {
                    records
                }
            }
        }
    }
}
