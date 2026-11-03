package com.example.turnos.reservation.infrastructure.kafka.consumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Optional;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.Acknowledgment;

import com.example.turnos.reservation.application.service.ReservationService;
import com.example.turnos.reservation.domain.model.EventHandlingResult;
import com.example.turnos.reservation.domain.model.ReservationEvent;
import com.example.turnos.reservation.domain.model.ReservationEventType;
import com.example.turnos.reservation.infrastructure.kafka.mapper.ReservationEventMessageMapper;
import com.example.turnos.shared.infrastructure.config.ReservationProperties;

// Sin Spring ni broker: se prueba qué hace el adaptador con cada mensaje y cuándo confirma el offset.
class ReservationEventListenerTest {

	private static final ReservationEvent EVENT = ReservationEvent.builder().eventId("e-1")
			.type(ReservationEventType.APPOINTMENT_CONFIRMED).reservationProcessId("p-1").build();

	private final ReservationService reservationService = mock(ReservationService.class);
	private final ReservationEventMessageMapper messageMapper = mock(ReservationEventMessageMapper.class);
	private final Acknowledgment acknowledgment = mock(Acknowledgment.class);
	private final ReservationEventListener listener = new ReservationEventListener(reservationService, messageMapper,
			new ReservationProperties(Duration.ofDays(7), 3, Duration.ofSeconds(60), Duration.ofSeconds(30)));

	@Test
	void handsTheEventToTheServiceAndThenConfirmsTheOffset() {
		when(messageMapper.toDomain("json")).thenReturn(Optional.of(EVENT));
		when(reservationService.handleReservationEvent(EVENT)).thenReturn(EventHandlingResult.APPLIED);

		listener.onReservationEvent(record("json"), acknowledgment);

		verify(reservationService, times(1)).handleReservationEvent(EVENT);
		verify(acknowledgment).acknowledge();
	}

	@Test
	void confirmsTheOffsetOfAMessageThatCannotBeUsedWithoutCallingTheService() {
		when(messageMapper.toDomain("basura")).thenReturn(Optional.empty());

		listener.onReservationEvent(record("basura"), acknowledgment);

		verify(reservationService, never()).handleReservationEvent(any());
		verify(acknowledgment).acknowledge();
	}

	@Test
	void retriesAFailureAndStopsAsSoonAsItWorks() {
		when(messageMapper.toDomain("json")).thenReturn(Optional.of(EVENT));
		when(reservationService.handleReservationEvent(EVENT)).thenThrow(new IllegalStateException("la base no responde"))
				.thenThrow(new IllegalStateException("la base no responde")).thenReturn(EventHandlingResult.APPLIED);

		listener.onReservationEvent(record("json"), acknowledgment);

		verify(reservationService, times(3)).handleReservationEvent(EVENT);
		verify(acknowledgment).acknowledge();
	}

	// ADR-0031: la entrega original y 3 reintentos; agotados, se sigue y se confirma el offset.
	@Test
	void givesUpAfterTheRetriesAndStillConfirmsTheOffset() {
		when(messageMapper.toDomain("json")).thenReturn(Optional.of(EVENT));
		when(reservationService.handleReservationEvent(EVENT)).thenThrow(new IllegalStateException("la base no responde"));

		listener.onReservationEvent(record("json"), acknowledgment);

		verify(reservationService, times(4)).handleReservationEvent(EVENT);
		verify(acknowledgment).acknowledge();
	}

	private static ConsumerRecord<String, String> record(String value) {
		return new ConsumerRecord<>("topic", 0, 5L, "p-1", value);
	}

}
