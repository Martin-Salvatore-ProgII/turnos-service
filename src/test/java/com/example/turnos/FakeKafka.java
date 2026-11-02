package com.example.turnos;

import java.time.Duration;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringSerializer;
import org.testcontainers.kafka.KafkaContainer;

/**
 * Un Kafka real (contenedor) que hace de Kafka de la cátedra (ADR-0044). Es uno solo para toda la
 * ejecución de las pruebas: levantarlo en cada contexto de Spring sumaría varios segundos a cada uno.
 * Los eventos de una prueba no molestan a otra: cada contexto consume con su propio grupo y los eventos
 * de procesos que no conoce se ignoran (ADR-0031).
 */
public final class FakeKafka {

	/** Cuánto esperar a que el consumidor reciba un mensaje recién publicado. */
	public static final Duration CONSUME_TIMEOUT = Duration.ofSeconds(20);

	private static KafkaContainer shared;

	private FakeKafka() {
	}

	/** El Kafka compartido, ya levantado. Lo detiene Ryuk al terminar la ejecución. */
	public static synchronized KafkaContainer shared() {
		if (shared == null) {
			shared = new KafkaContainer("apache/kafka:3.9.1");
			shared.start();
		}
		return shared;
	}

	public static String bootstrapServers() {
		return shared().getBootstrapServers();
	}

	/** Publica un mensaje con la key dada y espera a que el broker lo confirme. */
	public static RecordMetadata publish(String topic, String key, String value) {
		Properties config = new Properties();
		config.put("bootstrap.servers", bootstrapServers());
		config.put("key.serializer", StringSerializer.class.getName());
		config.put("value.serializer", StringSerializer.class.getName());
		try (KafkaProducer<String, String> producer = new KafkaProducer<>(config)) {
			return producer.send(new ProducerRecord<>(topic, key, value)).get(10, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(e);
		} catch (ExecutionException | TimeoutException e) {
			throw new IllegalStateException(e);
		}
	}

	/** El offset que el grupo confirmó en la partición del mensaje, o -1 si todavía no confirmó nada. */
	public static long committedOffset(String groupId, String topic, int partition) {
		try (Admin admin = Admin.create(Map.<String, Object>of("bootstrap.servers", bootstrapServers()))) {
			Map<TopicPartition, OffsetAndMetadata> offsets = admin.listConsumerGroupOffsets(groupId)
					.partitionsToOffsetAndMetadata().get(10, TimeUnit.SECONDS);
			OffsetAndMetadata committed = offsets.get(new TopicPartition(topic, partition));
			return committed == null ? -1 : committed.offset();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(e);
		} catch (ExecutionException | TimeoutException e) {
			throw new IllegalStateException(e);
		}
	}

}
