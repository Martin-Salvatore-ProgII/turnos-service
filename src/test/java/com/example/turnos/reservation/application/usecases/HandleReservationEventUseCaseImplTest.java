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
		ReservationStatus from = switch (type) {
			case INFORMATION_REJECTED -> ReservationStatus.PHONE_SUBMITTED;
			// La única salida de un estado final (maquina-estados.md, regla 2).
			case APPOINTMENT_CANCELLED -> ReservationStatus.CONFIRMED;
			default -> ReservationStatus.AWAITING_REQUEST;
		};
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
			case APPOINTMENT_CANCELLED -> ReservationStatus.CANCELLED;
		});
		verify(reservationProcessRepository).update(LOCAL_ID, process);
	}

	// CU-04, paso 8: el eventId del pedido se guarda como requestEventId, con el mensaje y el vencimiento.
	@Test
	void aPhoneRequestKeepsItsEventIdAsRequestEventIdWithTheMessageAndTheExpiration() {
		ReservationProcess process = process(ReservationStatus.AWAITING_REQUEST);
		Instant expiresAt = Instant.parse("2026-11-02T15:10:00Z");
		when(reservationProcessRepository.findByReservationProcessId(PROCESS_ID)).thenReturn(Optional.of(process));
		when(processedEventRepository.registerIfNew("pedido-1", NOW)).thenReturn(true);
		when(reservationProcessRepository.update(LOCAL_ID, process)).thenReturn(Optional.of(process));

		useCase.handleReservationEvent(ReservationEvent.builder().eventId("pedido-1")
				.type(ReservationEventType.INFORMATION_REQUESTED).reservationProcessId(PROCESS_ID)
				.message("Ingrese un numero de telefono para continuar.").expiresAt(expiresAt).build());

		assertThat(process.getStatus()).isEqualTo(ReservationStatus.AWAITING_PHONE);
		assertThat(process.getRequestEventId()).isEqualTo("pedido-1");
		assertThat(process.getLastMessage()).isEqualTo("Ingrese un numero de telefono para continuar.");
		assertThat(process.getExpiresAt()).isEqualTo(expiresAt);
	}

	// CU-04, 8a: el pedido puede llegar antes de que turnos registre el 202 de la confirmación.
	@Test
	void aPhoneRequestAlsoMovesAProcessThatIsStillHeld() {
		ReservationProcess process = process(ReservationStatus.HELD);
		when(reservationProcessRepository.findByReservationProcessId(PROCESS_ID)).thenReturn(Optional.of(process));
		when(processedEventRepository.registerIfNew("pedido-1", NOW)).thenReturn(true);
		when(reservationProcessRepository.update(LOCAL_ID, process)).thenReturn(Optional.of(process));

		useCase.handleReservationEvent(ReservationEvent.builder().eventId("pedido-1")
				.type(ReservationEventType.INFORMATION_REQUESTED).reservationProcessId(PROCESS_ID).build());

		assertThat(process.getStatus()).isEqualTo(ReservationStatus.AWAITING_PHONE);
		assertThat(process.getRequestEventId()).isEqualTo("pedido-1");
	}

	@Test
	void aLatePhoneRequestDoesNotOverwriteTheDataOfAProcessAlreadyPastThatPoint() {
		ReservationProcess process = process(ReservationStatus.PHONE_SUBMITTED);
		process.setRequestEventId("pedido-original");
		when(reservationProcessRepository.findByReservationProcessId(PROCESS_ID)).thenReturn(Optional.of(process));
		when(processedEventRepository.registerIfNew("pedido-tardio", NOW)).thenReturn(true);

		assertThat(useCase.handleReservationEvent(ReservationEvent.builder().eventId("pedido-tardio")
				.type(ReservationEventType.INFORMATION_REQUESTED).reservationProcessId(PROCESS_ID).build()))
				.isEqualTo(EventHandlingResult.NO_TRANSITION);

		assertThat(process.getRequestEventId()).isEqualTo("pedido-original");
		assertThat(process.getStatus()).isEqualTo(ReservationStatus.PHONE_SUBMITTED);
	}

	// REF §15.7: el rechazo no es un estado aparte, vuelve a esperar el teléfono hasta expiresAt.
	@Test
	void aRejectionGoesBackToWaitingForThePhoneWithTheReasonAndTheMessage() {
		ReservationProcess process = process(ReservationStatus.PHONE_SUBMITTED);
		process.setRequestEventId("pedido-1");
		Instant expiresAt = Instant.parse("2026-11-02T15:10:00Z");
		when(reservationProcessRepository.findByReservationProcessId(PROCESS_ID)).thenReturn(Optional.of(process));
		when(processedEventRepository.registerIfNew("rechazo-1", NOW)).thenReturn(true);
		when(reservationProcessRepository.update(LOCAL_ID, process)).thenReturn(Optional.of(process));

		useCase.handleReservationEvent(ReservationEvent.builder().eventId("rechazo-1")
				.type(ReservationEventType.INFORMATION_REJECTED).reservationProcessId(PROCESS_ID)
				.reason("INVALID_PHONE_NUMBER").message("El telefono ingresado no es valido.").expiresAt(expiresAt)
				.build());

		assertThat(process.getStatus()).isEqualTo(ReservationStatus.AWAITING_PHONE);
		assertThat(process.getFailureReason()).isEqualTo("INVALID_PHONE_NUMBER");
		assertThat(process.getLastMessage()).isEqualTo("El telefono ingresado no es valido.");
		assertThat(process.getExpiresAt()).isEqualTo(expiresAt);
		// El nuevo envío responde al mismo pedido.
		assertThat(process.getRequestEventId()).isEqualTo("pedido-1");
	}

	@Test
	void aLateRejectionDoesNotReopenAConfirmedProcess() {
		ReservationProcess process = process(ReservationStatus.CONFIRMED);
		when(reservationProcessRepository.findByReservationProcessId(PROCESS_ID)).thenReturn(Optional.of(process));
		when(processedEventRepository.registerIfNew("rechazo-1", NOW)).thenReturn(true);

		assertThat(useCase.handleReservationEvent(ReservationEvent.builder().eventId("rechazo-1")
				.type(ReservationEventType.INFORMATION_REJECTED).reservationProcessId(PROCESS_ID)
				.reason("INVALID_PHONE_NUMBER").build())).isEqualTo(EventHandlingResult.NO_TRANSITION);

		assertThat(process.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
		assertThat(process.getFailureReason()).isNull();
	}

	@Test
	void aConfirmationKeepsTheReservationIdAndWhenItWasConfirmed() {
		ReservationProcess process = process(ReservationStatus.PHONE_SUBMITTED);
		Instant confirmedAt = Instant.parse("2026-09-10T14:36:01Z");
		when(reservationProcessRepository.findByReservationProcessId(PROCESS_ID)).thenReturn(Optional.of(process));
		when(processedEventRepository.registerIfNew("confirmacion-1", NOW)).thenReturn(true);
		when(reservationProcessRepository.update(LOCAL_ID, process)).thenReturn(Optional.of(process));

		useCase.handleReservationEvent(ReservationEvent.builder().eventId("confirmacion-1")
				.type(ReservationEventType.APPOINTMENT_CONFIRMED).reservationProcessId(PROCESS_ID)
				.reservationId(8251L).occurredAt(confirmedAt).build());

		assertThat(process.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
		assertThat(process.getReservationId()).isEqualTo(8251L);
		assertThat(process.getConfirmedAt()).isEqualTo(confirmedAt);
	}

	@Test
	void aConfirmationWithoutTimeUsesTheCurrentTime() {
		ReservationProcess process = process(ReservationStatus.AWAITING_PHONE);
		when(reservationProcessRepository.findByReservationProcessId(PROCESS_ID)).thenReturn(Optional.of(process));
		when(processedEventRepository.registerIfNew("confirmacion-1", NOW)).thenReturn(true);
		when(reservationProcessRepository.update(LOCAL_ID, process)).thenReturn(Optional.of(process));

		// Rige la regla 3 de maquina-estados.md: la cátedra manda aunque el envío del teléfono no se haya anotado.
		useCase.handleReservationEvent(ReservationEvent.builder().eventId("confirmacion-1")
				.type(ReservationEventType.APPOINTMENT_CONFIRMED).reservationProcessId(PROCESS_ID)
				.reservationId(8251L).build());

		assertThat(process.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
		assertThat(process.getConfirmedAt()).isEqualTo(NOW);
	}

	// REF §15.10: una reserva cancelada por otro camino (o cuyo 200 se perdió) queda cancelada con lo que
	// informa la cátedra, que es quien la canceló para este servicio.
	@Test
	void aCancellationKeepsWhenAndWhyAndThatTheCatedraInformedIt() {
		ReservationProcess process = process(ReservationStatus.CONFIRMED);
		Instant cancelledAt = Instant.parse("2026-09-11T15:10:00Z");
		when(reservationProcessRepository.findByReservationProcessId(PROCESS_ID)).thenReturn(Optional.of(process));
		when(processedEventRepository.registerIfNew("cancelacion-1", NOW)).thenReturn(true);
		when(reservationProcessRepository.update(LOCAL_ID, process)).thenReturn(Optional.of(process));

		assertThat(useCase.handleReservationEvent(ReservationEvent.builder().eventId("cancelacion-1")
				.type(ReservationEventType.APPOINTMENT_CANCELLED).reservationProcessId(PROCESS_ID)
				.reason("Cambio solicitado por el usuario").occurredAt(cancelledAt).build()))
				.isEqualTo(EventHandlingResult.APPLIED);

		assertThat(process.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
		assertThat(process.getCancelledAt()).isEqualTo(cancelledAt);
		assertThat(process.getCancellationReason()).isEqualTo("Cambio solicitado por el usuario");
		assertThat(process.getCancelledBy()).isEqualTo(ReservationProcess.CANCELLED_BY_CATEDRA);
	}

	@Test
	void aCancellationWithoutTimeUsesTheCurrentTime() {
		ReservationProcess process = process(ReservationStatus.CONFIRMED);
		when(reservationProcessRepository.findByReservationProcessId(PROCESS_ID)).thenReturn(Optional.of(process));
		when(processedEventRepository.registerIfNew("cancelacion-1", NOW)).thenReturn(true);
		when(reservationProcessRepository.update(LOCAL_ID, process)).thenReturn(Optional.of(process));

		useCase.handleReservationEvent(ReservationEvent.builder().eventId("cancelacion-1")
				.type(ReservationEventType.APPOINTMENT_CANCELLED).reservationProcessId(PROCESS_ID).build());

		assertThat(process.getCancelledAt()).isEqualTo(NOW);
		assertThat(process.getCancellationReason()).isNull();
	}

	// ADR-0026: el usuario ya canceló y el 200 dejó la reserva CANCELLED; el evento que la cátedra publica
	// después no cambia nada, ni siquiera quién la canceló.
	@Test
	void theEventAfterTheCancellationOfTheOwnerChangesNothing() {
		ReservationProcess process = process(ReservationStatus.CANCELLED);
		process.setCancelledBy("juan");
		process.setCancellationReason("No puedo ir");
		when(reservationProcessRepository.findByReservationProcessId(PROCESS_ID)).thenReturn(Optional.of(process));
		when(processedEventRepository.registerIfNew("cancelacion-1", NOW)).thenReturn(true);

		assertThat(useCase.handleReservationEvent(ReservationEvent.builder().eventId("cancelacion-1")
				.type(ReservationEventType.APPOINTMENT_CANCELLED).reservationProcessId(PROCESS_ID)
				.reason("No puedo ir").build()))
				.isEqualTo(EventHandlingResult.NO_TRANSITION);

		assertThat(process.getCancelledBy()).isEqualTo("juan");
		verify(reservationProcessRepository, never()).update(any(), any());
	}

	@Test
	void anExpiredOrInvalidProcessKeepsTheReasonOfTheCatedra() {
		ReservationProcess expired = process(ReservationStatus.AWAITING_PHONE);
		ReservationProcess invalid = process(ReservationStatus.PHONE_SUBMITTED);
		when(reservationProcessRepository.findByReservationProcessId(PROCESS_ID))
				.thenReturn(Optional.of(expired), Optional.of(invalid));
		when(processedEventRepository.registerIfNew(any(), any())).thenReturn(true);
		when(reservationProcessRepository.update(any(), any()))
				.thenAnswer(invocation -> Optional.of(invocation.getArgument(1)));

		useCase.handleReservationEvent(ReservationEvent.builder().eventId("vencido-1")
				.type(ReservationEventType.PROCESS_EXPIRED).reservationProcessId(PROCESS_ID).reason("HOLD_EXPIRED")
				.build());
		useCase.handleReservationEvent(ReservationEvent.builder().eventId("invalido-1")
				.type(ReservationEventType.PROCESS_INVALID).reservationProcessId(PROCESS_ID)
				.reason("REQUEST_EVENT_MISMATCH").build());

		assertThat(expired.getStatus()).isEqualTo(ReservationStatus.EXPIRED);
		assertThat(expired.getFailureReason()).isEqualTo("HOLD_EXPIRED");
		assertThat(invalid.getStatus()).isEqualTo(ReservationStatus.INVALID);
		assertThat(invalid.getFailureReason()).isEqualTo("REQUEST_EVENT_MISMATCH");
	}

	// ADR-0023: un estado final no se reabre. Cada evento tardío, de cualquier tipo, se ignora, salvo la
	// cancelación de uno confirmado, que es la única salida de un estado final.
	@ParameterizedTest
	@EnumSource(value = ReservationStatus.class, names = { "CONFIRMED", "CANCELLED", "EXPIRED", "INVALID", "FAILED" })
	void noLateEventReopensAFinalProcess(ReservationStatus finalStatus) {
		for (ReservationEventType type : ReservationEventType.values()) {
			if (finalStatus == ReservationStatus.CONFIRMED && type == ReservationEventType.APPOINTMENT_CANCELLED) {
				continue;
			}
			ReservationProcess process = process(finalStatus);
			process.setReservationId(8251L);
			when(reservationProcessRepository.findByReservationProcessId(PROCESS_ID)).thenReturn(Optional.of(process));
			when(processedEventRepository.registerIfNew("tardio-" + type, NOW)).thenReturn(true);

			assertThat(useCase.handleReservationEvent(ReservationEvent.builder().eventId("tardio-" + type)
					.type(type).reservationProcessId(PROCESS_ID).reservationId(1L).reason("X").build()))
					.isEqualTo(EventHandlingResult.NO_TRANSITION);

			assertThat(process.getStatus()).isEqualTo(finalStatus);
			assertThat(process.getReservationId()).isEqualTo(8251L);
			assertThat(process.getFailureReason()).isNull();
		}
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
