package com.example.turnos.reservation.application.usecases;

import java.util.List;

import com.example.turnos.reservation.domain.model.PageQuery;
import com.example.turnos.reservation.domain.model.PageResult;
import com.example.turnos.reservation.domain.model.ReservationProcess;
import com.example.turnos.reservation.domain.model.ReservationStatus;
import com.example.turnos.reservation.domain.ports.in.ListReservationsUseCase;
import com.example.turnos.reservation.domain.ports.out.ReservationProcessRepository;
import com.example.turnos.user.domain.ports.out.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ListReservationsUseCaseImpl implements ListReservationsUseCase {

	private final UserRepository userRepository;
	private final ReservationProcessRepository reservationProcessRepository;

	// Como ver una reserva: el dueño se aplica en la consulta misma, así una reserva ajena nunca sale de
	// la base, y solo se leen datos locales, así que responde aunque la cátedra o el catálogo no estén
	// (ADR-0025, HU-08 criterio 3).
	@Override
	public PageResult<ReservationProcess> listReservations(String login, ReservationStatus status, PageQuery page) {
		return userRepository.findByLogin(login)
				.map(user -> reservationProcessRepository.findByUserId(user.getId(), status, page))
				// Un login sin usuario (por ejemplo, un token de un usuario que ya no existe) no tiene
				// reservas: una página vacía, no un error.
				.orElseGet(() -> PageResult.<ReservationProcess>builder()
						.content(List.of())
						.totalElements(0)
						.page(page.getPage())
						.size(page.getSize())
						.build());
	}

}
