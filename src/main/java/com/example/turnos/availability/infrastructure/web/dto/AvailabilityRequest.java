package com.example.turnos.availability.infrastructure.web.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

// Los parámetros de GET /api/availability, los dos obligatorios (docs/arq/contratos.md). Que la fecha
// esté dentro del horizonte es una regla del caso de uso (DATE_OUT_OF_RANGE), no de formato.
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AvailabilityRequest {

	@NotNull
	private Long professionalId;

	@NotNull
	@DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
	private LocalDate date;

}
