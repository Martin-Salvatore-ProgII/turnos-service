package com.example.turnos.reservation.domain.ports.in;

import java.util.Optional;
import java.util.UUID;

import com.example.turnos.reservation.domain.model.ReservationWithOwner;

public interface GetAnyReservationUseCase {

	// La reserva del id, de cualquier usuario, con su dueño (HU-10, criterio 2). Vacío si no existe. Solo
	// para el administrador: lo garantiza la ruta (ADR-0037), no este caso de uso.
	Optional<ReservationWithOwner> getAnyReservation(UUID id);

}
