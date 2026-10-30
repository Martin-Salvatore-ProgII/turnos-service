package com.example.turnos.reservation.infrastructure.rest.dto;

import java.time.LocalDate;
import java.time.LocalTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Pedido de POST /api/appointment-holds (REF §9). Todos los campos son obligatorios. La hora sale como
// HH:mm:ss; la cátedra acepta ese formato y HH:mm (probado contra la cátedra real).
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HoldRequest {
	private Long professionalId;
	private LocalDate date;
	private LocalTime startTime;
	private String externalPatientId;
}
