package com.example.turnos.reservation.application.usecases;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import com.example.turnos.reservation.domain.model.PageQuery;
import com.example.turnos.reservation.domain.model.PageResult;
import com.example.turnos.reservation.domain.model.ReservationOwner;
import com.example.turnos.reservation.domain.model.ReservationProcess;
import com.example.turnos.reservation.domain.model.ReservationSearchCriteria;
import com.example.turnos.reservation.domain.model.ReservationWithOwner;
import com.example.turnos.reservation.domain.ports.in.ListAllReservationsUseCase;
import com.example.turnos.reservation.domain.ports.out.ReservationProcessRepository;
import com.example.turnos.user.domain.model.User;
import com.example.turnos.user.domain.ports.out.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class ListAllReservationsUseCaseImpl implements ListAllReservationsUseCase {

	private final UserRepository userRepository;
	private final ReservationProcessRepository reservationProcessRepository;

	// Los usuarios son de la feature user: se leen por su puerto y no con un JOIN desde la consulta de
	// reservas (ADR-0064). Son hasta tres lecturas (el dueño del filtro, la página y sus dueños), en una
	// sola transacción para que vean el mismo estado de la base. Solo datos locales: responde aunque la
	// cátedra o el catálogo no estén (ADR-0025).
	@Override
	@Transactional(readOnly = true)
	public PageResult<ReservationWithOwner> listAllReservations(ReservationSearchCriteria criteria, PageQuery page) {
		ReservationSearchCriteria resolved = criteria;
		if (criteria.getOwnerLogin() != null) {
			// Login exacto. Los logins se guardan en minúsculas (HU-01), así que "Juan" encuentra a "juan".
			Optional<User> owner = userRepository.findByLogin(criteria.getOwnerLogin().toLowerCase(Locale.ROOT));
			if (owner.isEmpty()) {
				// Nadie con ese login: no tiene reservas. Una página vacía, como cualquier filtro sin resultados.
				return emptyPage(page);
			}
			resolved = criteria.toBuilder().ownerId(owner.get().getId()).build();
		}

		PageResult<ReservationProcess> processes = reservationProcessRepository.search(resolved, page);
		Map<Long, ReservationOwner> owners = ownersOf(processes.getContent());
		return processes.map(process -> ReservationWithOwner.builder()
				.process(process)
				.owner(owners.get(process.getUserId()))
				.build());
	}

	// Los dueños de toda la página en una sola consulta, no una por reserva.
	private Map<Long, ReservationOwner> ownersOf(List<ReservationProcess> processes) {
		if (processes.isEmpty()) {
			return Map.of();
		}
		List<Long> ids = processes.stream().map(ReservationProcess::getUserId).distinct().toList();
		return userRepository.findAllById(ids).stream()
				.collect(Collectors.toMap(User::getId, ReservationOwner::of));
	}

	private static PageResult<ReservationWithOwner> emptyPage(PageQuery page) {
		return PageResult.<ReservationWithOwner>builder()
				.content(List.of())
				.totalElements(0)
				.page(page.getPage())
				.size(page.getSize())
				.build();
	}

}
