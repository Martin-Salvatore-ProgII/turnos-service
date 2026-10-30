package com.example.turnos.reservation.infrastructure.web.dto;

import java.time.LocalDate;
import java.time.LocalTime;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// El cuerpo de POST /api/reservations (docs/arq/contratos.md). La hora se acepta como HH:mm o HH:mm:ss
// (REF §4). Que el turno exista en la agenda y la fecha esté en el horizonte son reglas del caso de uso,
// no de formato. El usuario no viaja acá: sale del JWT (RNF-03).
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReservationRequest {

	@NotNull
	private Long professionalId;

	@NotNull
	private LocalDate date;

	@NotNull
	private LocalTime startTime;

}
