package com.example.turnos.reservation.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import com.example.turnos.reservation.domain.model.ReservationProcess;
import com.example.turnos.reservation.domain.ports.out.ReservationProcessRepository;
import com.example.turnos.user.domain.model.User;
import com.example.turnos.user.domain.ports.out.UserRepository;
import org.junit.jupiter.api.Test;

// Ver una reserva propia, con JUnit y Mockito. Que una ajena no salga de la base lo prueba el adaptador
// (findByIdAndUserId) y el recorrido completo, ReservationFlowTest.
class GetReservationUseCaseImplTest {

	private static final UUID ID = UUID.randomUUID();

	private final UserRepository userRepository = mock(UserRepository.class);
	private final ReservationProcessRepository reservationProcessRepository = mock(ReservationProcessRepository.class);
	private final GetReservationUseCaseImpl useCase = new GetReservationUseCaseImpl(userRepository,
			reservationProcessRepository);

	@Test
	void looksForTheReservationAmongTheOnesOfTheUser() {
		ReservationProcess process = ReservationProcess.builder().id(ID).userId(7L).build();
		when(userRepository.findByLogin("juan")).thenReturn(Optional.of(User.builder().id(7L).login("juan").build()));
		when(reservationProcessRepository.findByIdAndUserId(ID, 7L)).thenReturn(Optional.of(process));

		assertThat(useCase.getReservation("juan", ID)).contains(process);
	}

	@Test
	void aReservationThatIsNotOfTheUserIsEmpty() {
		when(userRepository.findByLogin("juan")).thenReturn(Optional.of(User.builder().id(7L).login("juan").build()));
		when(reservationProcessRepository.findByIdAndUserId(ID, 7L)).thenReturn(Optional.empty());

		assertThat(useCase.getReservation("juan", ID)).isEmpty();
	}

	@Test
	void anUnknownUserHasNoReservations() {
		when(userRepository.findByLogin("nadie")).thenReturn(Optional.empty());

		assertThat(useCase.getReservation("nadie", ID)).isEmpty();
		verifyNoInteractions(reservationProcessRepository);
	}

}
