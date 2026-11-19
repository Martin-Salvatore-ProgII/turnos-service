package com.example.turnos.reservation.application.usecases;

import java.util.Optional;
import java.util.UUID;

import com.example.turnos.reservation.domain.model.ReservationOwner;
import com.example.turnos.reservation.domain.model.ReservationWithOwner;
import com.example.turnos.reservation.domain.ports.in.GetAnyReservationUseCase;
import com.example.turnos.reservation.domain.ports.out.ReservationProcessRepository;
import com.example.turnos.user.domain.ports.out.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class GetAnyReservationUseCaseImpl implements GetAnyReservationUseCase {

	private final ReservationProcessRepository reservationProcessRepository;
	private final UserRepository userRepository;

	// Sin filtro por dueño, a diferencia de GetReservationUseCaseImpl: esa diferencia es toda la operación
	// administrativa, y por eso vive en otro caso de uso y otra ruta, no en un "if admin" (ADR-0037). El
	// dueño se lee por el puerto de user (ADR-0064), en la misma transacción que la reserva.
	@Override
	@Transactional(readOnly = true)
	public Optional<ReservationWithOwner> getAnyReservation(UUID id) {
		return reservationProcessRepository.findById(id)
				.map(process -> ReservationWithOwner.builder()
						.process(process)
						.owner(userRepository.findById(process.getUserId()).map(ReservationOwner::of).orElse(null))
						.build());
	}

}
