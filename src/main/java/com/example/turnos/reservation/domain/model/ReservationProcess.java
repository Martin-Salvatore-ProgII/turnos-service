package com.example.turnos.reservation.domain.model;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Un proceso de reserva de un usuario (docs/arq/modelo-db.md). El {@code id} es el local, el que
 * ve la app; {@code reservationProcessId} es el de la cátedra y no se expone.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReservationProcess {
	private UUID id;
	// Dueño del proceso. Toda consulta o cambio se filtra por él (arq/seguridad.md).
	private Long userId;
	private ReservationStatus status;
	// Datos históricos del profesional y del turno: no son el catálogo vigente (Constitución P-05).
	private Long professionalId;
	private String professionalFirstName;
	private String professionalLastName;
	private LocalDate appointmentDate;
	private LocalTime startTime;
	private LocalTime endTime;
	private String holdId;
	private String reservationProcessId;
	private Long reservationId;
	// Vencimiento que informa la cátedra. Guardarlo no extiende el hold (REF §9).
	private Instant expiresAt;
	private String requestEventId;
	private String submittedEventId;
	private String lastMessage;
	private String failureReason;
	private String cancellationReason;
	private String cancelledBy;
	private Instant createdAt;
	private Instant updatedAt;
	private Instant confirmedAt;
	private Instant cancelledAt;
	// La versión leída de la base. Viaja con el proceso para que un cambio hecho sobre una lectura
	// vieja no se guarde (ADR-0068). La asigna la persistencia; el dominio no la modifica.
	private Long version;

	// Aplica la regla de docs/arq/maquina-estados.md: si el disparador no tiene transición desde el
	// estado actual, no cambia nada y devuelve false para que quien lo llamó lo registre (ADR-0023).
	public boolean apply(ReservationTrigger trigger) {
		if (!trigger.appliesTo(status)) {
			return false;
		}
		status = trigger.getTarget();
		return true;
	}

}
