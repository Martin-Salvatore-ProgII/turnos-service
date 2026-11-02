package com.example.turnos.reservation.domain.ports.out;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.example.turnos.reservation.domain.model.ReservationProcess;
import com.example.turnos.reservation.domain.model.ReservationStatus;

public interface ReservationProcessRepository {

	// Guarda un proceso nuevo, con el id ya asignado y sin versión. Si el usuario ya tiene un proceso
	// activo, la base lo rechaza y el adaptador lanza ReservationException con ACTIVE_RESERVATION_EXISTS
	// (ADR-0024, ADR-0070).
	ReservationProcess create(ReservationProcess process);

	// El proceso, solo si es de ese usuario. Uno de otro usuario da vacío, igual que uno inexistente
	// (arq/seguridad.md).
	Optional<ReservationProcess> findByIdAndUserId(UUID id, Long userId);

	// El proceso por su id local, sin filtrar por dueño: lo usa el servicio por su cuenta, no un pedido del
	// usuario (por ejemplo, para releer un proceso que cambió en el medio, ADR-0068).
	Optional<ReservationProcess> findById(UUID id);

	// El proceso al que pertenece un evento de la cátedra, por su reservationProcessId. Los eventos no
	// traen al usuario, así que acá no se filtra por dueño.
	Optional<ReservationProcess> findByReservationProcessId(String reservationProcessId);

	// Para la reconciliación (CU-05, ADR-0030). Los procesos que quedaron en STARTED desde antes de la fecha:
	// el hold nunca se registró, y si existió ya venció.
	List<ReservationProcess> findStartedCreatedBefore(Instant cutoff);

	// Los procesos en alguno de esos estados cuyo vencimiento pasó antes de la fecha.
	List<ReservationProcess> findByStatusInAndExpiresAtBefore(Collection<ReservationStatus> statuses, Instant cutoff);

	// Las reservas confirmadas con turno desde esa fecha en adelante: las que todavía se podrían cancelar.
	List<ReservationProcess> findConfirmedFrom(LocalDate date);

	// Si el usuario tiene algún proceso en un estado no final.
	boolean existsActiveByUserId(Long userId);

	// Guarda los cambios de un proceso existente. Vacío si no existe. Si el proceso cambió en la base
	// después de leerlo (otra versión), no guarda nada y el adaptador lanza
	// ReservationProcessConflictException (ADR-0068).
	Optional<ReservationProcess> update(UUID id, ReservationProcess process);

}
