package com.example.turnos.shared.infrastructure.config;

import java.util.HashMap;
import java.util.Map;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.ContainerProperties;

/**
 * Consumidor y productor de Kafka de la cátedra. Los servidores y el consumer group salen de la
 * integración que se pide al arrancar (ADR-0005), no de las propiedades {@code spring.kafka.*}. Los
 * topics también: el consumidor los toma de la integración con SpEL y el productor los recibe de su
 * adaptador.
 *
 * <p>Los valores viajan como texto: el adaptador de entrada interpreta el JSON, así un evento mal
 * formado llega al listener y se registra (ADR-0031) en lugar de fallar dentro del deserializador.
 */
@Configuration
public class KafkaConfig {

	@Bean
	public ConsumerFactory<String, String> catedraConsumerFactory(CatedraIntegration integration) {
		Map<String, Object> config = new HashMap<>();
		config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, integration.kafkaBootstrapServers());
		config.put(ConsumerConfig.GROUP_ID_CONFIG, integration.kafkaConsumerGroupId());
		config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
		config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
		// El offset se confirma a mano, después de persistir (ADR-0031).
		config.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
		// A diferencia del catálogo, acá no se empieza por lo último: un evento de reserva que llegó con
		// el servicio caído no se recupera de otra forma que releyéndolo. Los eventos ya vistos se
		// descartan por su eventId (ADR-0008) y los de procesos desconocidos se ignoran (ADR-0031).
		config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
		return new DefaultKafkaConsumerFactory<>(config);
	}

	// Se llama como la que Spring Boot crearía por defecto, para reemplazarla.
	@Bean
	public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
			ConsumerFactory<String, String> catedraConsumerFactory) {
		ConcurrentKafkaListenerContainerFactory<String, String> factory = new ConcurrentKafkaListenerContainerFactory<>();
		factory.setConsumerFactory(catedraConsumerFactory);
		// El listener recibe un Acknowledgment y lo confirma cuando decide; el commit sale en el momento.
		factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
		return factory;
	}

	@Bean
	public ProducerFactory<String, String> catedraProducerFactory(CatedraIntegration integration,
			CatedraProperties catedraProperties) {
		Map<String, Object> config = new HashMap<>();
		config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, integration.kafkaBootstrapServers());
		config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
		config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
		// El teléfono solo se da por enviado cuando el broker lo confirmó en todas sus réplicas (ADR-0029).
		config.put(ProducerConfig.ACKS_CONFIG, "all");
		// Sin reintentos ocultos: los únicos son los de ADR-0029, que quedan en el log (robustez.md, 7).
		// La idempotencia del productor exige reintentos, así que se apaga: la de este servicio es el eventId.
		config.put(ProducerConfig.RETRIES_CONFIG, 0);
		config.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, false);
		// Todas las esperas del envío valen lo mismo que el timeout de respuesta de la cátedra (RNF-09, 5 s,
		// configurable): el hilo nunca queda colgado más allá de lo ya decidido (ADR-0029).
		// linger.ms en 0: se publica un mensaje suelto por teléfono y no hay nada que agrupar. Además, Kafka
		// exige delivery.timeout >= linger + request.timeout, y su valor por defecto (5 ms) lo rompería.
		config.put(ProducerConfig.LINGER_MS_CONFIG, 0);
		int timeoutMs = (int) catedraProperties.readTimeout().toMillis();
		config.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, timeoutMs);
		config.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, timeoutMs);
		config.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, timeoutMs);
		return new DefaultKafkaProducerFactory<>(config);
	}

	@Bean
	public KafkaTemplate<String, String> catedraKafkaTemplate(ProducerFactory<String, String> catedraProducerFactory) {
		return new KafkaTemplate<>(catedraProducerFactory);
	}

}
