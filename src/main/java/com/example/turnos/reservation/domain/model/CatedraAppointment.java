package com.example.turnos.reservation.domain.model;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Una reserva tal como la informa la cátedra en {@code GET /api/appointments} (REF §11). Es la fuente de
 * verdad con la que la reconciliación corrige el estado local (ADR-0030). Solo lleva lo que se usa.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CatedraAppointment {
	private String reservationProcessId;
	private Long reservationId;
	private CatedraAppointmentStatus status;
	private Instant confirmedAt;
	private Instant cancelledAt;
	private String cancellationReason;
}
