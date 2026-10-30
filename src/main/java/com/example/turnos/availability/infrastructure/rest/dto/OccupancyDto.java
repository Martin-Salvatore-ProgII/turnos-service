package com.example.turnos.availability.infrastructure.rest.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

import com.example.turnos.availability.domain.model.OccupancyStatus;

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
public class OccupancyDto {
	private LocalDate date;
	// Se aceptan HH:mm y HH:mm:ss (REF §4): es el formato ISO de LocalTime, sin un patrón fijo.
	private LocalTime startTime;
	private OccupancyStatus status;
	private Instant expiresAt;
}
