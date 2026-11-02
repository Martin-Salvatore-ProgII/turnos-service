package com.example.turnos.shared.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.ContainerProperties;

// Sin Spring ni broker: solo se comprueba qué configuración reciben el consumidor y el productor.
class KafkaConfigTest {

	private static final CatedraIntegration INTEGRATION = new CatedraIntegration("grupo-de-prueba",
			"kafka.prueba.internal:9092", "grupo-consumidor", "topic-acciones", "topic-telefono", "PROVISIONED");

	private final KafkaConfig config = new KafkaConfig();
	private final ConsumerFactory<String, String> consumerFactory = config.catedraConsumerFactory(INTEGRATION);
	private final ProducerFactory<String, String> producerFactory = config.catedraProducerFactory(INTEGRATION);

	@Test
	void takesServersAndConsumerGroupFromTheIntegration() {
		Map<String, Object> properties = consumerFactory.getConfigurationProperties();

		assertThat(properties).containsEntry("bootstrap.servers", "kafka.prueba.internal:9092");
		assertThat(properties).containsEntry("group.id", "grupo-consumidor");
		assertThat(producerFactory.getConfigurationProperties()).containsEntry("bootstrap.servers",
				"kafka.prueba.internal:9092");
	}

	@Test
	void readsAndWritesRawText() {
		Map<String, Object> consumer = consumerFactory.getConfigurationProperties();
		Map<String, Object> producer = producerFactory.getConfigurationProperties();

		assertThat(consumer).containsEntry("key.deserializer", StringDeserializer.class);
		assertThat(consumer).containsEntry("value.deserializer", StringDeserializer.class);
		assertThat(producer).containsEntry("key.serializer", StringSerializer.class);
		assertThat(producer).containsEntry("value.serializer", StringSerializer.class);
	}

	@Test
	void doesNotCommitOffsetsOnItsOwnAndReadsFromTheStartWithoutOffset() {
		Map<String, Object> properties = consumerFactory.getConfigurationProperties();

		assertThat(properties).containsEntry("enable.auto.commit", false);
		assertThat(properties).containsEntry("auto.offset.reset", "earliest");
	}

	@Test
	void listenerAcknowledgesManuallyAndImmediately() {
		ConcurrentKafkaListenerContainerFactory<String, String> factory = config
				.kafkaListenerContainerFactory(consumerFactory);

		assertThat(factory.getContainerProperties().getAckMode())
				.isEqualTo(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
	}

	@Test
	void producerWaitsForAllReplicasAndNeverRetriesByItself() {
		Map<String, Object> properties = producerFactory.getConfigurationProperties();

		assertThat(properties).containsEntry("acks", "all");
		assertThat(properties).containsEntry("retries", 0);
		assertThat(properties).containsEntry("enable.idempotence", false);
	}

	@Test
	void producerWaitsAtMostTheCatedraResponseTimeout() {
		Map<String, Object> properties = producerFactory.getConfigurationProperties();

		assertThat(properties).containsEntry("max.block.ms", 5000);
		assertThat(properties).containsEntry("request.timeout.ms", 5000);
		assertThat(properties).containsEntry("delivery.timeout.ms", 5000);
	}

}
