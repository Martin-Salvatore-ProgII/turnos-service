package com.example.turnos.reservation.infrastructure.kafka.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.util.UUID;

import org.apache.kafka.clients.producer.RecordMetadata;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.example.turnos.FakeKafka;
import com.example.turnos.TestCatedraConfiguration;
import com.example.turnos.TestCatalogConfiguration;
import com.example.turnos.TestJwtKeysConfiguration;
import com.example.turnos.TestcontainersConfiguration;
import com.example.turnos.reservation.application.service.ReservationService;
import com.example.turnos.reservation.domain.model.ReservationEvent;
import com.example.turnos.reservation.domain.model.ReservationEventType;
import com.example.turnos.shared.infrastructure.config.CatedraIntegration;

// El consumidor real con un Kafka real: el topic, los servidores y el grupo salen de la integración
// (ADR-0005), el evento llega a la fachada y el offset se confirma después. La fachada es un mock: lo
// que se prueba acá es la conexión con Kafka, no las reglas (ReservationEventFlowTest).
@SpringBootTest
@Import({ TestcontainersConfiguration.class, TestJwtKeysConfiguration.class, TestCatedraConfiguration.class,
		TestCatalogConfiguration.class })
class ReservationEventKafkaTest {

	@MockitoBean
	private ReservationService reservationService;

	@Autowired
	private CatedraIntegration integration;

	@Test
	void deliversTheEventToTheServiceAndThenConfirmsTheOffset() {
		String eventId = "event-" + UUID.randomUUID();

		RecordMetadata sent = publish("p-1", event(eventId, "AppointmentProcessExpired", "p-1"));

		verify(reservationService, timeout(FakeKafka.CONSUME_TIMEOUT.toMillis())).handleReservationEvent(
				org.mockito.ArgumentMatchers.argThat((ReservationEvent e) -> e.getEventId().equals(eventId)
						&& e.getType() == ReservationEventType.PROCESS_EXPIRED));
		assertOffsetConfirmed(sent);
	}

	@Test
	void confirmsTheOffsetOfMalformedAndUnknownEventsWithoutCallingTheService() {
		publish("p-2", "esto no es JSON");
		RecordMetadata unknown = publish("p-2", event("event-" + UUID.randomUUID(), "AlgoNuevo", "p-2"));

		assertOffsetConfirmed(unknown);
		// Solo los eventos de este proceso: el consumidor lee desde el principio del topic y el mock es de toda
		// la clase, así que ve también los eventos que publicaron las otras pruebas.
		verify(reservationService, never()).handleReservationEvent(
				org.mockito.ArgumentMatchers.argThat((ReservationEvent e) -> e != null && "p-2".equals(e.getReservationProcessId())));
	}

	// ADR-0031: ante una falla persistente se intenta la entrega original y 3 reintentos, y se sigue.
	@Test
	void retriesAFailingEventThreeTimesAndThenConfirmsTheOffset() {
		String eventId = "event-" + UUID.randomUUID();
		doThrow(new IllegalStateException("la base no responde")).when(reservationService)
				.handleReservationEvent(org.mockito.ArgumentMatchers.argThat((ReservationEvent e) -> e != null
						&& e.getEventId().equals(eventId)));

		RecordMetadata sent = publish("p-3", event(eventId, "AppointmentConfirmed", "p-3"));

		verify(reservationService, timeout(FakeKafka.CONSUME_TIMEOUT.toMillis()).times(4)).handleReservationEvent(
				org.mockito.ArgumentMatchers.argThat((ReservationEvent e) -> e != null && e.getEventId().equals(eventId)));
		assertOffsetConfirmed(sent);
	}

	private RecordMetadata publish(String key, String value) {
		return FakeKafka.publish(integration.kafkaAppointmentActionsTopic(), key, value);
	}

	private void assertOffsetConfirmed(RecordMetadata sent) {
		await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(FakeKafka
				.committedOffset(integration.kafkaConsumerGroupId(), sent.topic(), sent.partition()))
				.isGreaterThan(sent.offset()));
	}

	private static String event(String eventId, String eventType, String processId) {
		return "{\"eventId\":\"" + eventId + "\",\"eventType\":\"" + eventType + "\",\"reservationProcessId\":\""
				+ processId + "\",\"reason\":\"HOLD_EXPIRED\",\"occurredAt\":\"2026-09-10T14:44:52Z\",\"schemaVersion\":1}";
	}

}
