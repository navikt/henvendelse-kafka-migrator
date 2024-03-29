package no.nav.henvendelsemigrator.utils.kafka

import no.nav.henvendelsemigrator.Config
import no.nav.henvendelsemigrator.utils.EnvUtils.getRequiredProperty
import no.nav.henvendelsemigrator.utils.kafka.KafkaEnvironmentVariables.KAFKA_CREDSTORE_PASSWORD
import no.nav.henvendelsemigrator.utils.kafka.KafkaEnvironmentVariables.KAFKA_KEYSTORE_PATH
import no.nav.henvendelsemigrator.utils.kafka.KafkaEnvironmentVariables.KAFKA_TRUSTSTORE_PATH
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.config.SslConfigs
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import java.time.Duration
import java.util.*

object KafkaUtils {
    const val henvendelseTopic = "personoversikt.henvendelse-henvendelse"
    const val endringsloggTopic = "personoversikt.henvendelse-endringlogg"
    const val consumerGroupId = "henvendelse-kafka-migrator-consumer"

    enum class MissingOffsetStrategy(val kafkaPropValue: String) {
        THROW_EXCEPTION("none"),
        START_AT_BEGINNING("earliest"),
        CONTINUE_FROM_LATEST("latest")
    }

    fun producerConfig(clientId: String, config: Config): Properties {
        val properties = Properties()
        properties[ProducerConfig.ACKS_CONFIG] = "all"
        properties[ProducerConfig.LINGER_MS_CONFIG] = 1000
        properties[ProducerConfig.CLIENT_ID_CONFIG] = clientId
        properties[ProducerConfig.BOOTSTRAP_SERVERS_CONFIG] = config.kafkaBrokers

        properties.apply(config.kafkaSecurityConfig)

        properties[ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.java
        properties[ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.java

        return properties
    }

    fun consumerConfig(groupId: String?, clientId: String?, missingOffsetStrategy: MissingOffsetStrategy, config: Config): Properties {
        val properties = Properties()
        properties[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = missingOffsetStrategy.kafkaPropValue
        properties[ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG] = false
        properties[ConsumerConfig.MAX_POLL_RECORDS_CONFIG] = 1000
        properties[ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG] = Duration.ofMinutes(10).toMillis().toInt()
        groupId?.also { properties[ConsumerConfig.GROUP_ID_CONFIG] = it }
        clientId?.also { properties[ConsumerConfig.CLIENT_ID_CONFIG] = it }
        properties[CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG] = config.kafkaBrokers

        properties.apply(config.kafkaSecurityConfig)

        properties[ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG] = StringDeserializer::class.java
        properties[ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG] = StringDeserializer::class.java

        return properties
    }

    fun aivenSecurityConfig(properties: Properties) {
        properties[CommonClientConfigs.SECURITY_PROTOCOL_CONFIG] = "SSL"
        properties[SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG] = getRequiredProperty(KAFKA_KEYSTORE_PATH)
        properties[SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG] = getRequiredProperty(KAFKA_CREDSTORE_PASSWORD)
        properties[SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG] = getRequiredProperty(KAFKA_TRUSTSTORE_PATH)
        properties[SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG] = getRequiredProperty(KAFKA_CREDSTORE_PASSWORD)
    }
}
