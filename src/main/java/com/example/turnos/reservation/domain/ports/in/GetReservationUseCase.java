package com.example.turnos.reservation.domain.ports.in;

import java.util.Optional;
import java.util.UUID;

import com.example.turnos.reservation.domain.model.ReservationProcess;

public interface GetReservationUseCase {

	// La reserva del id, solo si es del usuario del login (el del JWT). Vacío si no existe o es de otro
	// usuario: las dos respuestas tienen que ser iguales (HU-08, criterio 2).
	Optional<ReservationProcess> getReservation(String login, UUID id);

}
