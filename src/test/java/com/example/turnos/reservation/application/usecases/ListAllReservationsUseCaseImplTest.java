package com.example.turnos.reservation.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.example.turnos.reservation.domain.model.PageQuery;
import com.example.turnos.reservation.domain.model.PageResult;
import com.example.turnos.reservation.domain.model.ReservationProcess;
import com.example.turnos.reservation.domain.model.ReservationSearchCriteria;
import com.example.turnos.reservation.domain.model.ReservationStatus;
import com.example.turnos.reservation.domain.model.ReservationWithOwner;
import com.example.turnos.reservation.domain.ports.out.ReservationProcessRepository;
import com.example.turnos.user.domain.model.User;
import com.example.turnos.user.domain.ports.out.UserRepository;
import org.junit.jupiter.api.Test;

// HU-10, criterio 1: el listado de todas las reservas, con JUnit y Mockito, sin Spring ni base. Lo que se
// prueba es cómo el caso de uso combina las dos features: el login del filtro se resuelve a un id con el
// puerto de user, y los dueños de la página se leen en una sola consulta (ADR-0064).
class ListAllReservationsUseCaseImplTest {

	private static final PageQuery PAGE = PageQuery.builder().page(0).size(20).build();

	private final UserRepository userRepository = mock(UserRepository.class);
	private final ReservationProcessRepository reservationProcessRepository = mock(ReservationProcessRepository.class);
	private final ListAllReservationsUseCaseImpl useCase =
			new ListAllReservationsUseCaseImpl(userRepository, reservationProcessRepository);

	@Test
	void withoutFiltersListsEveryReservationWithItsOwner() {
		ReservationProcess juans = process(1L);
		ReservationProcess anas = process(2L);
		when(reservationProcessRepository.search(any(), eq(PAGE))).thenReturn(page(juans, anas));
		when(userRepository.findAllById(anyCollection())).thenReturn(List.of(user(1L, "juan"), user(2L, "ana")));

		PageResult<ReservationWithOwner> result = useCase.listAllReservations(ReservationSearchCriteria.builder().build(), PAGE);

		assertThat(result.getContent()).extracting(ReservationWithOwner::getProcess).containsExactly(juans, anas);
		assertThat(result.getContent()).extracting(r -> r.getOwner().getLogin()).containsExactly("juan", "ana");
		assertThat(result.getTotalElements()).isEqualTo(2);
		// Sin filtro por dueño no se busca ningún login.
		verify(userRepository, never()).findByLogin(any());
	}

	@Test
	void theOwnersOfThePageAreReadInASingleQueryWithoutRepeatingIds() {
		when(reservationProcessRepository.search(any(), eq(PAGE)))
				.thenReturn(page(process(1L), process(1L), process(2L)));
		when(userRepository.findAllById(anyCollection())).thenReturn(List.of(user(1L, "juan"), user(2L, "ana")));

		useCase.listAllReservations(ReservationSearchCriteria.builder().build(), PAGE);

		verify(userRepository, times(1)).findAllById(argThat(ids -> ids.size() == 2 && ids.containsAll(List.of(1L, 2L))));
	}

	@Test
	void theOwnerLoginIsResolvedToItsIdAndTheOtherFiltersPassAsTheyCame() {
		when(userRepository.findByLogin("juan")).thenReturn(Optional.of(user(1L, "juan")));
		when(reservationProcessRepository.search(any(), eq(PAGE))).thenReturn(page());
		ReservationSearchCriteria criteria = ReservationSearchCriteria.builder()
				.ownerLogin("juan")
				.status(ReservationStatus.CONFIRMED)
				.from(LocalDate.parse("2026-11-01"))
				.to(LocalDate.parse("2026-11-30"))
				.build();

		useCase.listAllReservations(criteria, PAGE);

		verify(reservationProcessRepository).search(argThat(c -> c.getOwnerId() == 1L
				&& c.getStatus() == ReservationStatus.CONFIRMED
				&& c.getFrom().equals(LocalDate.parse("2026-11-01"))
				&& c.getTo().equals(LocalDate.parse("2026-11-30"))), eq(PAGE));
	}

	@Test
	void theOwnerLoginIsComparedInLowerCase() {
		when(userRepository.findByLogin("juan")).thenReturn(Optional.of(user(1L, "juan")));
		when(reservationProcessRepository.search(any(), eq(PAGE))).thenReturn(page());

		useCase.listAllReservations(ReservationSearchCriteria.builder().ownerLogin("Juan").build(), PAGE);

		verify(userRepository).findByLogin("juan");
	}

	@Test
	void anUnknownOwnerLoginIsAnEmptyPageWithoutSearchingReservations() {
		when(userRepository.findByLogin("nadie")).thenReturn(Optional.empty());

		PageResult<ReservationWithOwner> result =
				useCase.listAllReservations(ReservationSearchCriteria.builder().ownerLogin("nadie").build(), PAGE);

		assertThat(result.getContent()).isEmpty();
		assertThat(result.getTotalElements()).isZero();
		assertThat(result.getPage()).isZero();
		assertThat(result.getSize()).isEqualTo(20);
		// Sin dueño, no se busca: si se buscara con ownerId en null, saldrían las reservas de todos.
		verify(reservationProcessRepository, never()).search(any(), any());
	}

	@Test
	void anEmptyPageDoesNotLookForOwners() {
		when(reservationProcessRepository.search(any(), eq(PAGE))).thenReturn(page());

		useCase.listAllReservations(ReservationSearchCriteria.builder().build(), PAGE);

		verify(userRepository, never()).findAllById(anyCollection());
	}

	private static ReservationProcess process(Long userId) {
		return ReservationProcess.builder().id(UUID.randomUUID()).userId(userId).build();
	}

	private static PageResult<ReservationProcess> page(ReservationProcess... processes) {
		return PageResult.<ReservationProcess>builder()
				.content(List.of(processes))
				.totalElements(processes.length)
				.page(PAGE.getPage())
				.size(PAGE.getSize())
				.build();
	}

	private static User user(Long id, String login) {
		return User.builder().id(id).login(login).firstName("Nombre " + login).lastName("Apellido").build();
	}

}
