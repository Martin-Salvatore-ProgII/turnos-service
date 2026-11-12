package com.example.turnos.reservation.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
import com.example.turnos.reservation.domain.model.ReservationProcess;
import com.example.turnos.reservation.domain.model.ReservationStatus;
import com.example.turnos.reservation.domain.model.ReservationWithOwner;
import com.example.turnos.reservation.domain.ports.out.AppointmentPort;
import com.example.turnos.reservation.domain.ports.out.ReservationProcessRepository;
import com.example.turnos.user.domain.model.User;
import com.example.turnos.user.domain.ports.out.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

// HU-10, criterios 3 y 4: cancelar en nombre de un usuario, con JUnit y Mockito. Solo lo propio del
// administrador (busca sin dueño, registra su login, responde con el dueño). Las reglas de estado son las de
// ReservationCanceller y se prueban todas en CancelReservationUseCaseImplTest; acá alcanza con ver que se
// aplican.
class CancelAnyReservationUseCaseImplTest {

	// 12:30 en la Argentina (ADR-0016).
	private static final Instant NOW = Instant.parse("2026-11-02T15:30:00Z");
	private static final Instant CANCELLED_AT = Instant.parse("2026-11-02T15:30:02Z");
	private static final UUID ID = UUID.randomUUID();

	private final ReservationProcessRepository reservationProcessRepository = mock(ReservationProcessRepository.class);
	private final AppointmentPort appointmentPort = mock(AppointmentPort.class);
	private final UserRepository userRepository = mock(UserRepository.class);

	private CancelAnyReservationUseCaseImpl useCase;

	@BeforeEach
	void setUp() {
		useCase = new CancelAnyReservationUseCaseImpl(reservationProcessRepository,
				new ReservationCanceller(reservationProcessRepository, appointmentPort, Clock.fixed(NOW, ZoneOffset.UTC)),
				userRepository);
		when(reservationProcessRepository.update(any(), any()))
				.thenAnswer(invocation -> Optional.of(invocation.getArgument(1)));
		when(userRepository.findById(7L)).thenReturn(Optional.of(
				User.builder().id(7L).login("juan").firstName("Juan").lastName("Perez").build()));
	}

	@Test
	void cancelsTheReservationOfAnotherUserAndRecordsTheAdministrator() {
		when(reservationProcessRepository.findById(ID)).thenReturn(Optional.of(confirmed()));
		when(appointmentPort.cancelAppointment("process-a92b47d0", "Pedido por soporte")).thenReturn(CANCELLED_AT);

		ReservationWithOwner result = useCase.cancelAnyReservation("admin", ID, "Pedido por soporte");

		assertThat(result.getProcess().getStatus()).isEqualTo(ReservationStatus.CANCELLED);
		// Queda el login del administrador, no el del dueño: así se sabe que lo canceló un administrador y cuál.
		assertThat(result.getProcess().getCancelledBy()).isEqualTo("admin");
		assertThat(result.getProcess().getCancellationReason()).isEqualTo("Pedido por soporte");
		assertThat(result.getProcess().getCancelledAt()).isEqualTo(CANCELLED_AT);
		assertThat(result.getOwner().getLogin()).isEqualTo("juan");
		// Sin filtro por dueño.
		verify(reservationProcessRepository, never()).findByIdAndUserId(any(), any());
	}

	@Test
	void aMissingReservationIsNotFoundWithoutCallingTheCatedra() {
		when(reservationProcessRepository.findById(ID)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> useCase.cancelAnyReservation("admin", ID, "Pedido por soporte"))
				.isInstanceOf(ReservationException.class)
				.extracting("code").isEqualTo(ReservationException.Code.RESERVATION_NOT_FOUND);
		verifyNoInteractions(appointmentPort);
	}

	// Las mismas reglas de estado que el dueño: el administrador no fuerza transiciones (ADR-0037).
	@Test
	void aReservationThatIsNotConfirmedIsNotCancellableForTheAdministratorEither() {
		ReservationProcess failed = confirmed();
		failed.setStatus(ReservationStatus.FAILED);
		when(reservationProcessRepository.findById(ID)).thenReturn(Optional.of(failed));

		assertThatThrownBy(() -> useCase.cancelAnyReservation("admin", ID, "Pedido por soporte"))
				.isInstanceOf(ReservationException.class)
				.extracting("code").isEqualTo(ReservationException.Code.RESERVATION_NOT_CANCELLABLE);
		verifyNoInteractions(appointmentPort);
	}

	@Test
	void anAlreadyCancelledReservationIsASuccessThatKeepsWhoCancelledIt() {
		ReservationProcess cancelled = confirmed();
		cancelled.setStatus(ReservationStatus.CANCELLED);
		cancelled.setCancelledBy("juan");
		when(reservationProcessRepository.findById(ID)).thenReturn(Optional.of(cancelled));

		ReservationWithOwner result = useCase.cancelAnyReservation("admin", ID, "Pedido por soporte");

		assertThat(result.getProcess().getCancelledBy()).isEqualTo("juan");
		verifyNoInteractions(appointmentPort);
		verify(reservationProcessRepository, never()).update(any(), any());
	}

	private static ReservationProcess confirmed() {
		LocalTime start = LocalTime.parse("10:00");
		return ReservationProcess.builder().id(ID).userId(7L).status(ReservationStatus.CONFIRMED)
				.reservationProcessId("process-a92b47d0").appointmentDate(LocalDate.parse("2026-11-09"))
				.startTime(start).endTime(start.plusMinutes(30)).version(1L).build();
	}

}
