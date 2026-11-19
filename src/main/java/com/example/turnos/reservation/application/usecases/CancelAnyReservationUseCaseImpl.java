package com.example.turnos.reservation.application.usecases;

import java.util.UUID;

import com.example.turnos.reservation.application.exception.ReservationException;
import com.example.turnos.reservation.domain.model.ReservationOwner;
import com.example.turnos.reservation.domain.model.ReservationProcess;
import com.example.turnos.reservation.domain.model.ReservationWithOwner;
import com.example.turnos.reservation.domain.ports.in.CancelAnyReservationUseCase;
import com.example.turnos.reservation.domain.ports.out.ReservationProcessRepository;
import com.example.turnos.user.domain.ports.out.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CancelAnyReservationUseCaseImpl implements CancelAnyReservationUseCase {

	private final ReservationProcessRepository reservationProcessRepository;
	private final ReservationCanceller reservationCanceller;
	private final UserRepository userRepository;

	// Lo propio de la cancelación del administrador: la reserva se busca sin filtro por dueño, y queda
	// registrado el login del administrador, no el del dueño (HU-10, criterio 3). cancelledBy distinto del
	// login del dueño y distinto de CATEDRA es, entonces, una cancelación administrativa: no hace falta
	// otra columna. El motivo obligatorio es una regla de formato de la entrada: lo valida el pedido
	// (AdminCancelReservationRequest), como el resto de los formatos.
	@Override
	public ReservationWithOwner cancelAnyReservation(String adminLogin, UUID id, String reason) {
		ReservationProcess process = reservationProcessRepository.findById(id)
				.orElseThrow(() -> new ReservationException(ReservationException.Code.RESERVATION_NOT_FOUND,
						"Could not find reservation with id: " + id));
		ReservationProcess cancelled = reservationCanceller.cancel(process, reason, adminLogin);
		// El dueño, para que la respuesta sea la misma vista administrativa que el detalle (ADR-0064).
		return ReservationWithOwner.builder()
				.process(cancelled)
				.owner(userRepository.findById(cancelled.getUserId()).map(ReservationOwner::of).orElse(null))
				.build();
	}

}
