package com.example.turnos.reservation.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.example.turnos.reservation.application.exception.ReservationException;
import com.example.turnos.reservation.application.exception.ReservationProcessConflictException;
import com.example.turnos.reservation.domain.model.CatedraAppointment;
import com.example.turnos.reservation.domain.model.CatedraAppointmentStatus;
import com.example.turnos.reservation.domain.model.ReservationProcess;
import com.example.turnos.reservation.domain.model.ReservationStatus;
import com.example.turnos.reservation.domain.ports.out.AppointmentPort;
import com.example.turnos.reservation.domain.ports.out.ReservationProcessRepository;
import com.example.turnos.user.domain.model.User;
import com.example.turnos.user.domain.ports.out.UserRepository;

// CU-05 y ADR-0030, caso por caso, con el reloj fijado: nada espera de verdad.
class ReconcileReservationProcessesUseCaseImplTest {

	// 2026-11-02 12:30 en Argentina.
	private static final Instant NOW = Instant.parse("2026-11-02T15:30:00Z");
	private static final Duration MARGIN = Duration.ofSeconds(30);
	private static final Instant CUTOFF = NOW.minus(MARGIN);
	private static final UUID PATIENT = UUID.fromString("0f8fad5b-d9cb-469f-a165-70867728950e");

	private final ReservationProcessRepository repository = mock(ReservationProcessRepository.class);
	private final UserRepository userRepository = mock(UserRepository.class);
	private final AppointmentPort appointmentPort = mock(AppointmentPort.class);

	private ReconcileReservationProcessesUseCaseImpl useCase;

	@BeforeEach
	void setUp() {
		useCase = new ReconcileReservationProcessesUseCaseImpl(repository, userRepository, appointmentPort,
				Clock.fixed(NOW, ZoneOffset.UTC));
		when(userRepository.findById(7L)).thenReturn(Optional.of(User.builder().id(7L).externalPatientId(PATIENT).build()));
		when(repository.findStartedCreatedBefore(any())).thenReturn(List.of());
		when(repository.findByStatusInAndExpiresAtBefore(anyCollection(), any())).thenReturn(List.of());
		when(repository.findConfirmedFrom(any())).thenReturn(List.of());
		when(repository.update(any(), any())).thenAnswer(invocation -> Optional.of(invocation.getArgument(1)));
	}

	@Test
	void aProcessLeftInStartedBecomesFailedBecauseTheHoldWasNeverRegistered() {
		ReservationProcess started = process(ReservationStatus.STARTED, null);
		when(repository.findStartedCreatedBefore(CUTOFF)).thenReturn(List.of(started));

		assertThat(useCase.reconcileReservationProcesses(MARGIN)).isEqualTo(1);

		assertThat(started.getStatus()).isEqualTo(ReservationStatus.FAILED);
		assertThat(started.getFailureReason()).isEqualTo("INTEGRATION_UNAVAILABLE");
		verify(repository).update(started.getId(), started);
	}

	@Test
	void theProcessesWaitingWithoutPhoneExpireWithoutAskingTheCatedra() {
		ReservationProcess held = process(ReservationStatus.HELD, CUTOFF.minusSeconds(1));
		ReservationProcess awaitingRequest = process(ReservationStatus.AWAITING_REQUEST, CUTOFF.minusSeconds(1));
		ReservationProcess awaitingPhone = process(ReservationStatus.AWAITING_PHONE, CUTOFF.minusSeconds(1));
		when(repository.findByStatusInAndExpiresAtBefore(EnumSet.of(ReservationStatus.HELD,
				ReservationStatus.AWAITING_REQUEST, ReservationStatus.AWAITING_PHONE), CUTOFF))
				.thenReturn(List.of(held, awaitingRequest, awaitingPhone));

		assertThat(useCase.reconcileReservationProcesses(MARGIN)).isEqualTo(3);

		assertThat(List.of(held, awaitingRequest, awaitingPhone)).allSatisfy(process -> {
			assertThat(process.getStatus()).isEqualTo(ReservationStatus.EXPIRED);
			assertThat(process.getFailureReason()).isEqualTo("HOLD_EXPIRED");
		});
		org.mockito.Mockito.verifyNoInteractions(appointmentPort);
	}

	@Test
	void aSubmittedProcessTheCatedraConfirmedBecomesConfirmedWithItsReservationId() {
		ReservationProcess submitted = submitted("process-1");
		whenTheCatedraReports(appointment("process-1", CatedraAppointmentStatus.CONFIRMED, 8251L));

		assertThat(useCase.reconcileReservationProcesses(MARGIN)).isEqualTo(1);

		assertThat(submitted.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
		assertThat(submitted.getReservationId()).isEqualTo(8251L);
		// La respuesta no trae confirmedAt: se usa la hora de este servicio.
		assertThat(submitted.getConfirmedAt()).isEqualTo(NOW);
	}

	@Test
	void theConfirmationTimeOfTheCatedraWinsWhenItIsInformed() {
		ReservationProcess submitted = submitted("process-1");
		CatedraAppointment confirmed = appointment("process-1", CatedraAppointmentStatus.CONFIRMED, 8251L);
		confirmed.setConfirmedAt(Instant.parse("2026-11-02T15:00:00Z"));
		whenTheCatedraReports(confirmed);

		useCase.reconcileReservationProcesses(MARGIN);

		assertThat(submitted.getConfirmedAt()).isEqualTo(Instant.parse("2026-11-02T15:00:00Z"));
	}

	@Test
	void aSubmittedProcessTheCatedraFailedExpires() {
		ReservationProcess submitted = submitted("process-1");
		whenTheCatedraReports(appointment("process-1", CatedraAppointmentStatus.FAILED, 8251L));

		assertThat(useCase.reconcileReservationProcesses(MARGIN)).isEqualTo(1);

		assertThat(submitted.getStatus()).isEqualTo(ReservationStatus.EXPIRED);
		assertThat(submitted.getFailureReason()).isEqualTo("CATEDRA_REPORTED_FAILED");
	}

	@Test
	void aSubmittedProcessStillPendingOrMissingInTheCatedraIsLeftForTheNextCycle() {
		ReservationProcess pending = submitted("process-1");
		ReservationProcess missing = submitted("process-2");
		ReservationProcess unknownStatus = submitted("process-3");
		whenTheCatedraReports(appointment("process-1", CatedraAppointmentStatus.PHONE_PENDING, 1L),
				appointment("process-3", CatedraAppointmentStatus.UNKNOWN, 3L));

		assertThat(useCase.reconcileReservationProcesses(MARGIN)).isZero();

		assertThat(List.of(pending, missing, unknownStatus)).allSatisfy(
				process -> assertThat(process.getStatus()).isEqualTo(ReservationStatus.PHONE_SUBMITTED));
		verify(repository, never()).update(any(), any());
	}

	@Test
	void aConfirmedFutureReservationTheCatedraCancelledBecomesCancelled() {
		ReservationProcess confirmed = process(ReservationStatus.CONFIRMED, null);
		confirmed.setReservationProcessId("process-1");
		when(repository.findConfirmedFrom(LocalDate.parse("2026-11-02"))).thenReturn(List.of(confirmed));
		CatedraAppointment cancelled = appointment("process-1", CatedraAppointmentStatus.CANCELLED, 8251L);
		cancelled.setCancelledAt(Instant.parse("2026-11-01T10:00:00Z"));
		cancelled.setCancellationReason("Cambio solicitado");
		when(appointmentPort.findAppointments(PATIENT)).thenReturn(List.of(cancelled));

		assertThat(useCase.reconcileReservationProcesses(MARGIN)).isEqualTo(1);

		assertThat(confirmed.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
		assertThat(confirmed.getCancelledAt()).isEqualTo(Instant.parse("2026-11-01T10:00:00Z"));
		assertThat(confirmed.getCancellationReason()).isEqualTo("Cambio solicitado");
		assertThat(confirmed.getCancelledBy()).isEqualTo("CATEDRA");
	}

	@Test
	void aConfirmedReservationTheCatedraStillHasConfirmedIsLeftAlone() {
		ReservationProcess confirmed = process(ReservationStatus.CONFIRMED, null);
		confirmed.setReservationProcessId("process-1");
		when(repository.findConfirmedFrom(any())).thenReturn(List.of(confirmed));
		when(appointmentPort.findAppointments(PATIENT))
				.thenReturn(List.of(appointment("process-1", CatedraAppointmentStatus.CONFIRMED, 8251L)));

		assertThat(useCase.reconcileReservationProcesses(MARGIN)).isZero();

		verify(repository, never()).update(any(), any());
	}

	@Test
	void whenTheCatedraDoesNotAnswerTheProcessesStayAsTheyAre() {
		ReservationProcess submitted = submitted("process-1");
		when(appointmentPort.findAppointments(PATIENT)).thenThrow(
				new ReservationException(ReservationException.Code.INTEGRATION_UNAVAILABLE, "Catedra service unavailable"));

		assertThat(useCase.reconcileReservationProcesses(MARGIN)).isZero();

		assertThat(submitted.getStatus()).isEqualTo(ReservationStatus.PHONE_SUBMITTED);
	}

	@Test
	void anOwnerIsAskedOnceNoMatterHowManyOfTheirProcessesNeedTheCatedra() {
		ReservationProcess submitted = submitted("process-1");
		ReservationProcess confirmed = process(ReservationStatus.CONFIRMED, null);
		confirmed.setReservationProcessId("process-2");
		when(repository.findConfirmedFrom(any())).thenReturn(List.of(confirmed));
		when(appointmentPort.findAppointments(PATIENT)).thenReturn(List.of());

		useCase.reconcileReservationProcesses(MARGIN);

		// Una vez para los enviados y otra para los confirmados: dos fases, una consulta por dueño en cada una.
		verify(appointmentPort, times(2)).findAppointments(PATIENT);
		assertThat(submitted.getStatus()).isEqualTo(ReservationStatus.PHONE_SUBMITTED);
	}

	// Llegó un evento mientras se reconciliaba: el evento ganó y no se pisa (ADR-0068).
	@Test
	void aProcessThatChangedWhileReconcilingIsLeftForTheNextCycleAndTheOthersContinue() {
		ReservationProcess first = process(ReservationStatus.HELD, CUTOFF.minusSeconds(1));
		ReservationProcess second = process(ReservationStatus.HELD, CUTOFF.minusSeconds(1));
		when(repository.findByStatusInAndExpiresAtBefore(anyCollection(), any())).thenReturn(List.of(first, second));
		doThrow(new ReservationProcessConflictException(first.getId())).when(repository).update(first.getId(), first);

		assertThat(useCase.reconcileReservationProcesses(MARGIN)).isEqualTo(1);

		verify(repository).update(second.getId(), second);
		assertThat(second.getStatus()).isEqualTo(ReservationStatus.EXPIRED);
	}

	private ReservationProcess submitted(String reservationProcessId) {
		ReservationProcess process = process(ReservationStatus.PHONE_SUBMITTED, CUTOFF.minusSeconds(1));
		process.setReservationProcessId(reservationProcessId);
		List<ReservationProcess> all = new java.util.ArrayList<>(
				repository.findByStatusInAndExpiresAtBefore(EnumSet.of(ReservationStatus.PHONE_SUBMITTED), CUTOFF));
		all.add(process);
		when(repository.findByStatusInAndExpiresAtBefore(EnumSet.of(ReservationStatus.PHONE_SUBMITTED), CUTOFF))
				.thenReturn(all);
		return process;
	}

	private void whenTheCatedraReports(CatedraAppointment... appointments) {
		when(appointmentPort.findAppointments(PATIENT)).thenReturn(List.of(appointments));
	}

	private static CatedraAppointment appointment(String processId, CatedraAppointmentStatus status, Long reservationId) {
		return CatedraAppointment.builder().reservationProcessId(processId).status(status).reservationId(reservationId)
				.build();
	}

	private static ReservationProcess process(ReservationStatus status, Instant expiresAt) {
		return ReservationProcess.builder().id(UUID.randomUUID()).userId(7L).status(status).expiresAt(expiresAt).build();
	}

}
