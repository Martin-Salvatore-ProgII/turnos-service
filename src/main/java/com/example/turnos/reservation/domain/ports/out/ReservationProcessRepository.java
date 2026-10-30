package com.example.turnos.reservation.domain.ports.out;

import java.util.Optional;
import java.util.UUID;

import com.example.turnos.reservation.domain.model.ReservationProcess;

public interface ReservationProcessRepository {

	// Guarda un proceso nuevo, con el id ya asignado y sin versión.
	ReservationProcess create(ReservationProcess process);

	// Guarda los cambios de un proceso existente. Vacío si no existe. Si el proceso cambió en la base
	// después de leerlo (otra versión), no guarda nada y el adaptador lanza
	// ReservationProcessConflictException (ADR-0068).
	Optional<ReservationProcess> update(UUID id, ReservationProcess process);

}
