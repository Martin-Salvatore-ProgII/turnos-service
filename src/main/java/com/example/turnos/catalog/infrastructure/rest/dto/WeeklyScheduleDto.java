package com.example.turnos.catalog.infrastructure.rest.dto;

import java.time.DayOfWeek;
import java.time.LocalTime;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@JsonIgnoreProperties(ignoreUnknown = true)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WeeklyScheduleDto {
	private Long id;
	private DayOfWeek dayOfWeek;
	// Se aceptan HH:mm y HH:mm:ss (contratos.md): es el formato ISO de LocalTime, sin un patrón fijo.
	private LocalTime startTime;
	private LocalTime endTime;
	private Integer slotDurationMinutes;
}
