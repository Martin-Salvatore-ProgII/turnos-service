package com.example.turnos.reservation.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import com.example.turnos.reservation.domain.model.PageQuery;
import com.example.turnos.reservation.domain.model.PageResult;
import com.example.turnos.reservation.domain.model.ReservationProcess;
import com.example.turnos.reservation.domain.model.ReservationStatus;
import com.example.turnos.reservation.domain.ports.out.ReservationProcessRepository;
import com.example.turnos.user.domain.model.User;
import com.example.turnos.user.domain.ports.out.UserRepository;
import org.junit.jupiter.api.Test;

// Listar las reservas propias, con JUnit y Mockito. Que la consulta filtre por dueño, por estado y
// ordene lo prueba el adaptador contra PostgreSQL (JpaReservationProcessRepositoryAdapterTest).
class ListReservationsUseCaseImplTest {

	private static final PageQuery PAGE = PageQuery.builder().page(1).size(5).build();

	private final UserRepository userRepository = mock(UserRepository.class);
	private final ReservationProcessRepository reservationProcessRepository = mock(ReservationProcessRepository.class);
	private final ListReservationsUseCaseImpl useCase = new ListReservationsUseCaseImpl(userRepository,
			reservationProcessRepository);

	@Test
	void looksForTheReservationsOfTheUserWithTheFilterAndThePage() {
		PageResult<ReservationProcess> page = PageResult.<ReservationProcess>builder()
				.content(List.of(ReservationProcess.builder().userId(7L).build()))
				.totalElements(6)
				.page(1)
				.size(5)
				.build();
		when(userRepository.findByLogin("juan")).thenReturn(Optional.of(User.builder().id(7L).login("juan").build()));
		when(reservationProcessRepository.findByUserId(7L, ReservationStatus.CONFIRMED, PAGE)).thenReturn(page);

		assertThat(useCase.listReservations("juan", ReservationStatus.CONFIRMED, PAGE)).isSameAs(page);
	}

	@Test
	void anUnknownUserHasAnEmptyPage() {
		when(userRepository.findByLogin("nadie")).thenReturn(Optional.empty());

		PageResult<ReservationProcess> page = useCase.listReservations("nadie", null, PAGE);

		assertThat(page.getContent()).isEmpty();
		assertThat(page.getTotalElements()).isZero();
		assertThat(page.getPage()).isEqualTo(1);
		assertThat(page.getSize()).isEqualTo(5);
		verifyNoInteractions(reservationProcessRepository);
	}

}
