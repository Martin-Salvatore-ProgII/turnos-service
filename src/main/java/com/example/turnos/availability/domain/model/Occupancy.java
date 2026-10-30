package com.example.turnos.availability.domain.model;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Una posición no disponible de la agenda de un profesional, según la cátedra (REF §8). Se identifica
 * por la fecha y la hora de inicio; ocupa un slot sea {@code CONFIRMED} o {@code HELD} (ADR-0015).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Occupancy {
	private LocalDate date;
	private LocalTime startTime;
	private OccupancyStatus status;
	// Solo para HELD. Es el vencimiento del lado de la cátedra: una copia local no lo extiende.
	private Instant expiresAt;
}
