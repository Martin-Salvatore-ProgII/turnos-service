package com.example.turnos.reservation.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import com.example.turnos.reservation.domain.model.ReservationProcess;
import com.example.turnos.reservation.domain.model.ReservationWithOwner;
import com.example.turnos.reservation.domain.ports.out.ReservationProcessRepository;
import com.example.turnos.user.domain.model.User;
import com.example.turnos.user.domain.ports.out.UserRepository;
import org.junit.jupiter.api.Test;

// HU-10, criterio 2: el detalle de una reserva de cualquier usuario, con su dueño. JUnit y Mockito.
class GetAnyReservationUseCaseImplTest {

	private static final UUID ID = UUID.randomUUID();

	private final ReservationProcessRepository reservationProcessRepository = mock(ReservationProcessRepository.class);
	private final UserRepository userRepository = mock(UserRepository.class);
	private final GetAnyReservationUseCaseImpl useCase =
			new GetAnyReservationUseCaseImpl(reservationProcessRepository, userRepository);

	@Test
	void returnsTheReservationOfAnyUserWithItsOwner() {
		ReservationProcess process = ReservationProcess.builder().id(ID).userId(7L).build();
		when(reservationProcessRepository.findById(ID)).thenReturn(Optional.of(process));
		when(userRepository.findById(7L)).thenReturn(Optional.of(
				User.builder().id(7L).login("juan").firstName("Juan").lastName("Perez").email("juan@example.com").build()));

		ReservationWithOwner found = useCase.getAnyReservation(ID).orElseThrow();

		assertThat(found.getProcess()).isSameAs(process);
		assertThat(found.getOwner().getLogin()).isEqualTo("juan");
		assertThat(found.getOwner().getFirstName()).isEqualTo("Juan");
		assertThat(found.getOwner().getLastName()).isEqualTo("Perez");
		// Sin filtro por dueño: busca por id y nada más.
		verify(reservationProcessRepository, never()).findByIdAndUserId(any(), any());
	}

	@Test
	void aMissingReservationIsEmptyWithoutLookingForAnOwner() {
		when(reservationProcessRepository.findById(ID)).thenReturn(Optional.empty());

		assertThat(useCase.getAnyReservation(ID)).isEmpty();
		verify(userRepository, never()).findById(any());
	}

}
