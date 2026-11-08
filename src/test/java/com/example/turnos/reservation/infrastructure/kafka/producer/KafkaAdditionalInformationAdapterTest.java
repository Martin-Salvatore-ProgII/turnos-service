package com.example.turnos.reservation.infrastructure.kafka.producer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import com.example.turnos.FakeKafka;
import com.example.turnos.reservation.domain.model.PhoneSubmission;
import com.example.turnos.reservation.infrastructure.kafka.mapper.AdditionalInformationMessageMapper;
import com.example.turnos.shared.infrastructure.config.CatedraIntegration;
import com.example.turnos.shared.infrastructure.config.CatedraProperties;
import com.example.turnos.shared.infrastructure.config.CommandRetryProperties;
import com.example.turnos.shared.infrastructure.config.KafkaConfig;
import com.example.turnos.shared.infrastructure.rest.RemoteCommandRetry;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// El productor real de KafkaConfig contra un Kafka real, y reintentos probados con un KafkaTemplate que
// falla. Las esperas entre reintentos son de pocos milisegundos para que la prueba no tarde.
class KafkaAdditionalInformationAdapterTest {

	private static final String TOPIC = "catedra.turnos.telefono.proyecto-de-prueba";
	private static final JsonMapper JSON = JsonMapper.builder().build();
	// Con el broker real se usa el timeout normal (5 s): el primer envío a un topic nuevo lo crea el broker.
	// Con el inalcanzable, 1 s, para que la prueba no espere tres veces cinco segundos.
	private static final CatedraProperties PROPERTIES = new CatedraProperties("http://catedra.prueba.internal", "token",
			Duration.ofSeconds(2), Duration.ofSeconds(5));
	private static final CatedraProperties FAST = new CatedraProperties("http://catedra.prueba.internal", "token",
			Duration.ofSeconds(1), Duration.ofSeconds(1));
	private static final RemoteCommandRetry RETRY = new RemoteCommandRetry(
			new CommandRetryProperties(2, Duration.ofMillis(10)));

	@Test
	void publishesTheMessageOfTheContractWithTheProcessIdAsKey() {
		String processId = "process-" + UUID.randomUUID();
		KafkaAdditionalInformationAdapter adapter = adapterWith(realTemplate(FakeKafka.bootstrapServers(), PROPERTIES), PROPERTIES);

		boolean published = adapter.publishPhone(submission(processId, "phone-submitted-b91e"));

		assertThat(published).isTrue();
		List<ConsumerRecord<String, String>> records = FakeKafka.recordsWithKey(TOPIC, processId, 1, Duration.ofSeconds(20));
		assertThat(records).hasSize(1);
		JsonNode message = JSON.readTree(records.get(0).value());
		// REF §15.5: todos los campos obligatorios, y eventType y schemaVersion con exactamente estos valores.
		assertThat(message.get("eventId").asString()).isEqualTo("phone-submitted-b91e");
		assertThat(message.get("eventType").asString()).isEqualTo("AdditionalInformationSubmitted");
		assertThat(message.get("groupId").asString()).isEqualTo("proyecto-de-prueba");
		assertThat(message.get("reservationProcessId").asString()).isEqualTo(processId);
		assertThat(message.get("holdId").asString()).isEqualTo("hold-6c0c2f41");
		assertThat(message.get("requestEventId").asString()).isEqualTo("request-event-8a31");
		assertThat(message.get("information").get("phoneNumber").asString()).isEqualTo("+5492615551234");
		assertThat(message.get("occurredAt").asString()).isEqualTo("2026-11-02T15:30:00Z");
		assertThat(message.get("schemaVersion").asInt()).isEqualTo(1);
	}

	@Test
	void retriesWithExactlyTheSameMessageUntilTheBrokerConfirms() {
		@SuppressWarnings("unchecked")
		KafkaTemplate<String, String> template = mock(KafkaTemplate.class);
		CompletableFuture<SendResult<String, String>> failed = CompletableFuture.failedFuture(new RuntimeException("sin ack"));
		CompletableFuture<SendResult<String, String>> ok = CompletableFuture
				.completedFuture(new SendResult<>(null, mock(RecordMetadata.class)));
		when(template.send(anyString(), anyString(), anyString())).thenReturn(failed, failed, ok);

		boolean published = adapterWith(template, FAST).publishPhone(submission("process-1", "phone-submitted-1"));

		assertThat(published).isTrue();
		org.mockito.ArgumentCaptor<String> bodies = org.mockito.ArgumentCaptor.forClass(String.class);
		verify(template, times(3)).send(anyString(), anyString(), bodies.capture());
		assertThat(bodies.getAllValues()).containsOnly(bodies.getAllValues().get(0));
	}

	@Test
	void givesUpAfterTheRetriesWhenTheBrokerNeverConfirms() {
		@SuppressWarnings("unchecked")
		KafkaTemplate<String, String> template = mock(KafkaTemplate.class);
		when(template.send(anyString(), anyString(), anyString()))
				.thenReturn(CompletableFuture.failedFuture(new RuntimeException("sin ack")));

		assertThat(adapterWith(template, FAST).publishPhone(submission("process-1", "phone-submitted-1"))).isFalse();

		// Una vez y dos reintentos (ADR-0029), ni uno más.
		verify(template, times(3)).send(anyString(), anyString(), anyString());
	}

	@Test
	void reportsFailureWithoutHangingWhenTheBrokerIsUnreachable() {
		// Un puerto donde no escucha nadie: el productor corta por su propio timeout (1 s en esta prueba).
		KafkaAdditionalInformationAdapter adapter = adapterWith(realTemplate("localhost:1", FAST), FAST);

		assertThat(adapter.publishPhone(submission("process-1", "phone-submitted-1"))).isFalse();
	}

	private static KafkaAdditionalInformationAdapter adapterWith(KafkaTemplate<String, String> template,
			CatedraProperties properties) {
		return new KafkaAdditionalInformationAdapter(template, integration("localhost:1"), properties, RETRY,
				new AdditionalInformationMessageMapper(), JSON);
	}

	private static KafkaTemplate<String, String> realTemplate(String bootstrapServers, CatedraProperties properties) {
		return new KafkaTemplate<>(new KafkaConfig().catedraProducerFactory(integration(bootstrapServers), properties));
	}

	private static CatedraIntegration integration(String bootstrapServers) {
		return new CatedraIntegration("proyecto-de-prueba", bootstrapServers, "grupo", "acciones", TOPIC, "PROVISIONED");
	}

	private static PhoneSubmission submission(String processId, String eventId) {
		return PhoneSubmission.builder().eventId(eventId).reservationProcessId(processId).holdId("hold-6c0c2f41")
				.requestEventId("request-event-8a31").phoneNumber("+5492615551234")
				.occurredAt(Instant.parse("2026-11-02T15:30:00Z")).build();
	}

}
