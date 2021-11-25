package no.nav.henvendelsemigrator.introspect

import no.nav.henvendelsemigrator.Config
import no.nav.henvendelsemigrator.log
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
        val fromOffset: Long,
        val toOffset: Long,
        val filter: Map<String, String?>?,
    )

    class Task(private val config: Config) : IntrospectionTask<Input, List<KafkaRecord>>(
        name = "Les meldinger fra kafka",
        description = "Leser meldinger fra kafka topics: ${KafkaUtils.henvendelseTopic}, ${KafkaUtils.endringsloggTopic}",
        inputExample = Input(
            topic = KafkaUtils.henvendelseTopic,
            maxRecords = 10,
            fromOffset = 100,
            toOffset = 9007199254740991L,
            filter = mapOf(
                "aktorId" to null,
                "fnr" to null,
                "behandlingskjedeId" to null,
                "behandlingsId" to null
            )
        )
    ) {
        override fun action(input: Input): List<KafkaRecord> {
            val kafkaConsumer = KafkaConsumer<String, String>(KafkaUtils.consumerConfig(null, null, config))
            return kafkaConsumer.use { consumer ->
                val records = mutableListOf<KafkaRecord>()
                val topicPartition = TopicPartition(input.topic, 0)
                val fromOffset = java.lang.Long.max(0, input.fromOffset)
                val toOffset = java.lang.Long.max(0, input.toOffset)

                consumer.assign(listOf(topicPartition))
                consumer.seek(topicPartition, fromOffset)

                while (records.size < input.maxRecords) {
                    val consumerRecords = consumer.poll(Duration.ofMillis(1_000))
                    if (consumerRecords.isEmpty) {
                        break
                    }
                    consumerRecords
                        .map {
                            KafkaRecord(
                                timestamp = it.timestamp(),
                                offset = it.offset(),
                                key = it.key(),
                                value = it.value().fromJson<Map<String, Any?>>()
                            )
                        }
                        .filter(matches(input.filter))
                        .let { records.addAll(it) }
                    val currentOffset = consumerRecords.maxOfOrNull { it.offset() } ?: Long.MAX_VALUE
                    log.info("[ReadKafkaTopic] read ${input.topic}, reached offset $currentOffset")
                    if (currentOffset > toOffset) {
                        log.info("[ReadKafkaTopic] reached limit, stopping search")
                        break
                    }
                }
                if (records.size > input.maxRecords) {
                    records.subList(0, input.maxRecords)
                } else {
                    records
                }
            }
        }

        companion object {
            private fun matches(filter: Map<String, String?>?): (KafkaRecord) -> Boolean {
                val predicates: List<(KafkaRecord) -> Boolean> = (filter ?: emptyMap())
                    .entries
                    .mapNotNull { entry ->
                        val attribute: String = entry.key
                        val expectedValue: String? = entry.value
                        predicate(expectedValue, attribute)
                    }

                return { kafkaRecord -> predicates.all { it(kafkaRecord) } }
            }

            private fun predicate(expectedValue: String?, jsonProperty: String): ((KafkaRecord) -> Boolean)? {
                if (expectedValue == null) return null
                return { kafkaRecord ->
                    when (val value = kafkaRecord.value) {
                        is Map<*, *> -> value[jsonProperty] == expectedValue
                        else -> false
                    }
                }
            }
        }
    }
}