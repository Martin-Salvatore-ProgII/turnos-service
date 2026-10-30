package com.example.turnos.reservation.domain.ports.out;

import java.util.Optional;
import java.util.UUID;

import com.example.turnos.reservation.domain.model.ReservationProcess;

public interface ReservationProcessRepository {

	// Guarda un proceso nuevo, con el id ya asignado y sin versión. Si el usuario ya tiene un proceso
	// activo, la base lo rechaza y el adaptador lanza ReservationException con ACTIVE_RESERVATION_EXISTS
	// (ADR-0024, ADR-0070).
	ReservationProcess create(ReservationProcess process);

	// El proceso, solo si es de ese usuario. Uno de otro usuario da vacío, igual que uno inexistente
	// (arq/seguridad.md).
	Optional<ReservationProcess> findByIdAndUserId(UUID id, Long userId);

	// Si el usuario tiene algún proceso en un estado no final.
	boolean existsActiveByUserId(Long userId);

	// Guarda los cambios de un proceso existente. Vacío si no existe. Si el proceso cambió en la base
	// después de leerlo (otra versión), no guarda nada y el adaptador lanza
	// ReservationProcessConflictException (ADR-0068).
	Optional<ReservationProcess> update(UUID id, ReservationProcess process);

}
