package com.example.turnos.availability.domain.model;

import java.time.LocalTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Un turno posible de la agenda: hora local de Argentina (ADR-0016), sin fecha ni zona.
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Slot {
	private LocalTime startTime;
	private LocalTime endTime;
}
