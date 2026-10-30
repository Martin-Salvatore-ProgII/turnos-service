package com.example.turnos.reservation.application.usecases;

import java.util.Optional;
import java.util.UUID;

import com.example.turnos.reservation.domain.model.ReservationProcess;
import com.example.turnos.reservation.domain.ports.in.GetReservationUseCase;
import com.example.turnos.reservation.domain.ports.out.ReservationProcessRepository;
import com.example.turnos.user.domain.ports.out.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GetReservationUseCaseImpl implements GetReservationUseCase {

	private final UserRepository userRepository;
	private final ReservationProcessRepository reservationProcessRepository;

	// El dueño se aplica en la misma consulta, no después de leer: una reserva ajena nunca sale de la
	// base (arq/seguridad.md). Es la operación que la app consulta cada pocos segundos mientras el
	// proceso no es final (ADR-0021), y solo lee datos locales: responde aunque la cátedra o el
	// catálogo no estén (ADR-0025).
	@Override
	public Optional<ReservationProcess> getReservation(String login, UUID id) {
		return userRepository.findByLogin(login)
				.flatMap(user -> reservationProcessRepository.findByIdAndUserId(id, user.getId()));
	}

}
