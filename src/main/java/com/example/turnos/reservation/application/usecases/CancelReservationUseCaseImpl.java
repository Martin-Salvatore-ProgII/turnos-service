package com.example.turnos.reservation.application.usecases;

import java.util.UUID;

import com.example.turnos.reservation.application.exception.ReservationException;
import com.example.turnos.reservation.domain.model.ReservationProcess;
import com.example.turnos.reservation.domain.ports.in.CancelReservationUseCase;
import com.example.turnos.reservation.domain.ports.out.ReservationProcessRepository;
import com.example.turnos.user.domain.ports.out.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CancelReservationUseCaseImpl implements CancelReservationUseCase {

	private final UserRepository userRepository;
	private final ReservationProcessRepository reservationProcessRepository;
	private final ReservationCanceller reservationCanceller;

	// Lo propio de la cancelación del dueño: la reserva se busca con el dueño en la consulta misma (una
	// ajena responde igual que una inexistente) y queda registrado su login. Las reglas de estado son las
	// de ReservationCanceller, las mismas que aplica el administrador.
	@Override
	public ReservationProcess cancelReservation(String login, UUID id, String reason) {
		ReservationProcess process = userRepository.findByLogin(login)
				.flatMap(user -> reservationProcessRepository.findByIdAndUserId(id, user.getId()))
				.orElseThrow(() -> new ReservationException(ReservationException.Code.RESERVATION_NOT_FOUND,
						"Could not find reservation with id: " + id));
		return reservationCanceller.cancel(process, reason, login);
	}

}
