package com.example.turnos.reservation.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.turnos.reservation.application.exception.ReservationProcessConflictException;
import com.example.turnos.reservation.domain.model.EventHandlingResult;
import com.example.turnos.reservation.domain.model.ReservationEvent;
import com.example.turnos.reservation.domain.model.ReservationEventType;
import com.example.turnos.reservation.domain.model.ReservationProcess;
import com.example.turnos.reservation.domain.model.ReservationStatus;
import com.example.turnos.reservation.domain.ports.out.ProcessedEventRepository;
import com.example.turnos.reservation.domain.ports.out.ReservationProcessRepository;

@ExtendWith(MockitoExtension.class)
class HandleReservationEventUseCaseImplTest {

	private static final Instant NOW = Instant.parse("2026-11-02T15:00:00Z");
	private static final String PROCESS_ID = "process-a92b47d0";
	private static final UUID LOCAL_ID = UUID.randomUUID();

	@Mock
	private ReservationProcessRepository reservationProcessRepository;

	@Mock
	private ProcessedEventRepository processedEventRepository;

	private HandleReservationEventUseCaseImpl useCase;

	@BeforeEach
	void setUp() {
		useCase = new HandleReservationEventUseCaseImpl(reservationProcessRepository, processedEventRepository,
				Clock.fixed(NOW, ZoneOffset.UTC));
	}

	@Test
	void ignoresAnEventOfAnUnknownProcessWithoutRegisteringIt() {
		when(reservationProcessRepository.findByReservationProcessId(PROCESS_ID)).thenReturn(Optional.empty());

		assertThat(useCase.handleReservationEvent(event(ReservationEventType.APPOINTMENT_CONFIRMED)))
				.isEqualTo(EventHandlingResult.UNKNOWN_PROCESS);

		verifyNoInteractions(processedEventRepository);
		verify(reservationProcessRepository, never()).update(any(), any());
	}

	@Test
	void ignoresADuplicateEventWithoutChangingTheProcess() {
		when(reservationProcessRepository.findByReservationProcessId(PROCESS_ID))
				.thenReturn(Optional.of(process(ReservationStatus.AWAITING_REQUEST)));
		when(processedEventRepository.registerIfNew("evento-1", NOW)).thenReturn(false);

		assertThat(useCase.handleReservationEvent(event(ReservationEventType.INFORMATION_REQUESTED)))
				.isEqualTo(EventHandlingResult.DUPLICATE);

		verify(reservationProcessRepository, never()).update(any(), any());
	}

	@Test
	void ignoresAnEventWithNoTransitionFromTheCurrentStatus() {
		when(reservationProcessRepository.findByReservationProcessId(PROCESS_ID))
				.thenReturn(Optional.of(process(ReservationStatus.CONFIRMED)));
		when(processedEventRepository.registerIfNew("evento-1", NOW)).thenReturn(true);

		// Un vencimiento tardío no reabre un proceso que ya se confirmó (ADR-0023).
		assertThat(useCase.handleReservationEvent(event(ReservationEventType.PROCESS_EXPIRED)))
				.isEqualTo(EventHandlingResult.NO_TRANSITION);

		verify(reservationProcessRepository, never()).update(any(), any());
	}

	@ParameterizedTest
	@EnumSource(ReservationEventType.class)
	void everyEventTypeMovesAWaitingProcessToItsStatus(ReservationEventType type) {
		ReservationStatus from = type == ReservationEventType.INFORMATION_REJECTED ? ReservationStatus.PHONE_SUBMITTED
				: ReservationStatus.AWAITING_REQUEST;
		ReservationProcess process = process(from);
		when(reservationProcessRepository.findByReservationProcessId(PROCESS_ID)).thenReturn(Optional.of(process));
		when(processedEventRepository.registerIfNew("evento-1", NOW)).thenReturn(true);
		when(reservationProcessRepository.update(LOCAL_ID, process)).thenReturn(Optional.of(process));

		assertThat(useCase.handleReservationEvent(event(type))).isEqualTo(EventHandlingResult.APPLIED);

		assertThat(process.getStatus()).isEqualTo(switch (type) {
			case INFORMATION_REQUESTED, INFORMATION_REJECTED -> ReservationStatus.AWAITING_PHONE;
			case APPOINTMENT_CONFIRMED -> ReservationStatus.CONFIRMED;
			case PROCESS_EXPIRED -> ReservationStatus.EXPIRED;
			case PROCESS_INVALID -> ReservationStatus.INVALID;
		});
		verify(reservationProcessRepository).update(LOCAL_ID, process);
	}

	@Test
	void letsAConflictOnSaveRiseSoTheConsumerRetries() {
		ReservationProcess process = process(ReservationStatus.AWAITING_REQUEST);
		when(reservationProcessRepository.findByReservationProcessId(PROCESS_ID)).thenReturn(Optional.of(process));
		when(processedEventRepository.registerIfNew("evento-1", NOW)).thenReturn(true);
		when(reservationProcessRepository.update(LOCAL_ID, process))
				.thenThrow(new ReservationProcessConflictException(LOCAL_ID));

		assertThatThrownBy(() -> useCase.handleReservationEvent(event(ReservationEventType.INFORMATION_REQUESTED)))
				.isInstanceOf(ReservationProcessConflictException.class);
	}

	private static ReservationEvent event(ReservationEventType type) {
		return ReservationEvent.builder().eventId("evento-1").type(type).reservationProcessId(PROCESS_ID).build();
	}

	private static ReservationProcess process(ReservationStatus status) {
		return ReservationProcess.builder().id(LOCAL_ID).status(status).reservationProcessId(PROCESS_ID).build();
	}

}
