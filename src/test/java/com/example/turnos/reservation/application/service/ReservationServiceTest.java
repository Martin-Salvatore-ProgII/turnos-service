package com.example.turnos.reservation.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;
import java.util.UUID;

import com.example.turnos.reservation.application.exception.ReservationException;
import com.example.turnos.reservation.domain.model.ReservationProcess;
import com.example.turnos.reservation.domain.ports.in.GetReservationUseCase;
import com.example.turnos.reservation.domain.ports.in.StartReservationUseCase;
import org.junit.jupiter.api.Test;

// La fachada: delega en los puertos de entrada y traduce el vacío en RESERVATION_NOT_FOUND (ADR-0059).
class ReservationServiceTest {

	private static final UUID ID = UUID.randomUUID();

	private final StartReservationUseCase startReservationUseCase = mock(StartReservationUseCase.class);
	private final GetReservationUseCase getReservationUseCase = mock(GetReservationUseCase.class);
	private final ReservationService service = new ReservationService(startReservationUseCase, getReservationUseCase);

	@Test
	void startReservationDelegatesToItsUseCase() {
		ReservationProcess process = ReservationProcess.builder().id(ID).build();
		LocalDate date = LocalDate.parse("2026-11-09");
		LocalTime start = LocalTime.parse("11:20");
		when(startReservationUseCase.startReservation("juan", 204L, date, start)).thenReturn(process);

		assertThat(service.startReservation("juan", 204L, date, start)).isSameAs(process);
	}

	@Test
	void getReservationReturnsTheReservationOfTheUser() {
		ReservationProcess process = ReservationProcess.builder().id(ID).build();
		when(getReservationUseCase.getReservation("juan", ID)).thenReturn(Optional.of(process));

		assertThat(service.getReservation("juan", ID)).isSameAs(process);
	}

	@Test
	void aMissingReservationIsReservationNotFound() {
		when(getReservationUseCase.getReservation("juan", ID)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.getReservation("juan", ID))
				.isInstanceOf(ReservationException.class)
				.extracting("code").isEqualTo(ReservationException.Code.RESERVATION_NOT_FOUND);
	}

}
