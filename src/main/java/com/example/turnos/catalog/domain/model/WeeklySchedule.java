package com.example.turnos.catalog.domain.model;

import java.time.DayOfWeek;
import java.time.LocalTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Las horas son hora local de Argentina (ADR-0016), sin zona: el cálculo de slots las combina con la
// fecha pedida.
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WeeklySchedule {
	private Long id;
	private DayOfWeek dayOfWeek;
	private LocalTime startTime;
	private LocalTime endTime;
	private int slotDurationMinutes;
}
