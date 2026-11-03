package com.example.turnos.reservation.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import com.example.turnos.reservation.application.exception.ReservationException;
import com.example.turnos.reservation.application.exception.ReservationProcessConflictException;
import com.example.turnos.reservation.domain.model.ReservationProcess;
import com.example.turnos.reservation.domain.model.ReservationStatus;
import com.example.turnos.reservation.domain.ports.out.AppointmentPort;
import com.example.turnos.reservation.domain.ports.out.ReservationProcessRepository;
import com.example.turnos.user.domain.model.User;
import com.example.turnos.user.domain.ports.out.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

// Cancelar una reserva propia (HU-09, ADR-0026, ADR-0071), con JUnit y Mockito. Cómo responde la cátedra
// lo prueba el adaptador (CatedraAppointmentAdapterTest); el recorrido completo, ReservationFlowTest.
class CancelReservationUseCaseImplTest {

	// 12:30 en la Argentina (ADR-0016).
	private static final Instant NOW = Instant.parse("2026-11-02T15:30:00Z");
	private static final Instant CANCELLED_AT = Instant.parse("2026-11-02T15:30:02Z");
	private static final UUID ID = UUID.randomUUID();

	private final UserRepository userRepository = mock(UserRepository.class);
	private final ReservationProcessRepository reservationProcessRepository = mock(ReservationProcessRepository.class);
	private final AppointmentPort appointmentPort = mock(AppointmentPort.class);

	private CancelReservationUseCaseImpl useCase;

	@BeforeEach
	void setUp() {
		useCase = new CancelReservationUseCaseImpl(userRepository, reservationProcessRepository, appointmentPort,
				Clock.fixed(NOW, ZoneOffset.UTC));
		when(userRepository.findByLogin("juan")).thenReturn(Optional.of(User.builder().id(7L).login("juan").build()));
		when(reservationProcessRepository.update(any(), any()))
				.thenAnswer(invocation -> Optional.of(invocation.getArgument(1)));
	}

	@Test
	void cancelsAtTheCatedraAndThenMovesToCancelledWithWhatItAnswered() {
		ReservationProcess process = confirmed();
		when(reservationProcessRepository.findByIdAndUserId(ID, 7L)).thenReturn(Optional.of(process));
		when(appointmentPort.cancelAppointment("process-a92b47d0", "No puedo ir")).thenReturn(CANCELLED_AT);

		ReservationProcess result = useCase.cancelReservation("juan", ID, "No puedo ir");

		assertThat(result.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
		assertThat(result.getCancelledAt()).isEqualTo(CANCELLED_AT);
		assertThat(result.getCancellationReason()).isEqualTo("No puedo ir");
		assertThat(result.getCancelledBy()).isEqualTo("juan");
		verify(reservationProcessRepository).update(ID, process);
	}

	@Test
	void withoutTheMomentOfTheCatedraUsesTheCurrentOne() {
		when(reservationProcessRepository.findByIdAndUserId(ID, 7L)).thenReturn(Optional.of(confirmed()));
		when(appointmentPort.cancelAppointment("process-a92b47d0", null)).thenReturn(null);

		ReservationProcess result = useCase.cancelReservation("juan", ID, null);

		assertThat(result.getCancelledAt()).isEqualTo(NOW);
		assertThat(result.getCancellationReason()).isNull();
	}

	// HU-09, criterio 2: éxito sin cambios y sin llamar a la cátedra.
	@Test
	void anAlreadyCancelledReservationIsReturnedAsItIs() {
		ReservationProcess cancelled = confirmed();
		cancelled.setStatus(ReservationStatus.CANCELLED);
		cancelled.setCancelledBy("CATEDRA");
		when(reservationProcessRepository.findByIdAndUserId(ID, 7L)).thenReturn(Optional.of(cancelled));

		ReservationProcess result = useCase.cancelReservation("juan", ID, "otro motivo");

		assertThat(result).isSameAs(cancelled);
		assertThat(result.getCancelledBy()).isEqualTo("CATEDRA");
		verifyNoInteractions(appointmentPort);
		verify(reservationProcessRepository, never()).update(any(), any());
	}

	// HU-09, criterio 4: una reserva ajena responde igual que una inexistente.
	@Test
	void rejectsAReservationThatIsNotTheirsOrDoesNotExist() {
		when(reservationProcessRepository.findByIdAndUserId(ID, 7L)).thenReturn(Optional.empty());

		assertRejected(ReservationException.Code.RESERVATION_NOT_FOUND);
		verifyNoInteractions(appointmentPort);
	}

	// HU-09, criterio 3: solo una reserva confirmada se puede cancelar.
	@ParameterizedTest
	@EnumSource(value = ReservationStatus.class, names = { "CONFIRMED", "CANCELLED" }, mode = EnumSource.Mode.EXCLUDE)
	void rejectsAReservationThatIsNotConfirmed(ReservationStatus status) {
		ReservationProcess process = confirmed();
		process.setStatus(status);
		when(reservationProcessRepository.findByIdAndUserId(ID, 7L)).thenReturn(Optional.of(process));

		assertRejected(ReservationException.Code.RESERVATION_NOT_CANCELLABLE);
		verifyNoInteractions(appointmentPort);
	}

	// ADR-0071: un turno que ya empezó, con la hora de la Argentina, no se cancela.
	@Test
	void rejectsAnAppointmentThatAlreadyStarted() {
		ReservationProcess process = confirmedAt(LocalDate.parse("2026-11-02"), LocalTime.parse("12:30"));
		when(reservationProcessRepository.findByIdAndUserId(ID, 7L)).thenReturn(Optional.of(process));

		assertRejected(ReservationException.Code.RESERVATION_NOT_CANCELLABLE);
		verifyNoInteractions(appointmentPort);
	}

	@Test
	void anAppointmentOfTodayThatDidNotStartIsCancelled() {
		ReservationProcess process = confirmedAt(LocalDate.parse("2026-11-02"), LocalTime.parse("12:31"));
		when(reservationProcessRepository.findByIdAndUserId(ID, 7L)).thenReturn(Optional.of(process));

		assertThat(useCase.cancelReservation("juan", ID, null).getStatus()).isEqualTo(ReservationStatus.CANCELLED);
	}

	// HU-09, criterio 5, y un rechazo de la cátedra: el adaptador lanza la excepción y la reserva no cambia.
	@ParameterizedTest
	@EnumSource(value = ReservationException.Code.class, names = { "INTEGRATION_UNAVAILABLE",
			"RESERVATION_NOT_CANCELLABLE" })
	void whenTheCatedraDoesNotCancelTheReservationDoesNotChange(ReservationException.Code code) {
		ReservationProcess process = confirmed();
		when(reservationProcessRepository.findByIdAndUserId(ID, 7L)).thenReturn(Optional.of(process));
		when(appointmentPort.cancelAppointment("process-a92b47d0", null))
				.thenThrow(new ReservationException(code, "no"));

		assertRejected(code);
		assertThat(process.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
		verify(reservationProcessRepository, never()).update(any(), any());
	}

	// AppointmentCancelled (o la reconciliación) llegó mientras se esperaba a la cátedra: queda lo que
	// registró ese camino, sin volver a guardar.
	@Test
	void aCancellationRecordedInTheMeantimeIsNotOverwritten() {
		ReservationProcess stale = confirmed();
		ReservationProcess cancelledByTheEvent = confirmed();
		cancelledByTheEvent.setStatus(ReservationStatus.CANCELLED);
		cancelledByTheEvent.setCancellationReason("Motivo del evento");
		when(reservationProcessRepository.findByIdAndUserId(ID, 7L)).thenReturn(Optional.of(stale));
		when(appointmentPort.cancelAppointment("process-a92b47d0", "No puedo ir")).thenReturn(CANCELLED_AT);
		doAnswer(invocation -> {
			throw new ReservationProcessConflictException(ID);
		}).when(reservationProcessRepository).update(any(), any());
		when(reservationProcessRepository.findById(ID)).thenReturn(Optional.of(cancelledByTheEvent));

		ReservationProcess result = useCase.cancelReservation("juan", ID, "No puedo ir");

		assertThat(result).isSameAs(cancelledByTheEvent);
		assertThat(result.getCancellationReason()).isEqualTo("Motivo del evento");
	}

	// Otro cambio que no fue una cancelación (por ejemplo, otra versión del mismo proceso): se relee y se
	// cancela sobre la versión nueva.
	@Test
	void aConflictWithAProcessThatIsStillConfirmedCancelsTheNewestVersion() {
		ReservationProcess stale = confirmed();
		ReservationProcess latest = confirmed();
		latest.setVersion(3L);
		when(reservationProcessRepository.findByIdAndUserId(ID, 7L)).thenReturn(Optional.of(stale));
		when(appointmentPort.cancelAppointment("process-a92b47d0", null)).thenReturn(CANCELLED_AT);
		doAnswer(invocation -> {
			throw new ReservationProcessConflictException(ID);
		}).when(reservationProcessRepository).update(ID, stale);
		doAnswer(invocation -> Optional.of(invocation.getArgument(1)))
				.when(reservationProcessRepository).update(ID, latest);
		when(reservationProcessRepository.findById(ID)).thenReturn(Optional.of(latest));

		ReservationProcess result = useCase.cancelReservation("juan", ID, null);

		assertThat(result).isSameAs(latest);
		assertThat(result.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
		assertThat(result.getCancelledBy()).isEqualTo("juan");
	}

	private void assertRejected(ReservationException.Code code) {
		assertThatThrownBy(() -> useCase.cancelReservation("juan", ID, null))
				.isInstanceOfSatisfying(ReservationException.class, e -> assertThat(e.getCode()).isEqualTo(code));
	}

	private static ReservationProcess confirmed() {
		return confirmedAt(LocalDate.parse("2026-11-09"), LocalTime.parse("10:00"));
	}

	private static ReservationProcess confirmedAt(LocalDate date, LocalTime startTime) {
		return ReservationProcess.builder().id(ID).userId(7L).status(ReservationStatus.CONFIRMED)
				.reservationProcessId("process-a92b47d0").appointmentDate(date).startTime(startTime)
				.endTime(startTime.plusMinutes(30)).version(1L).build();
	}

}
