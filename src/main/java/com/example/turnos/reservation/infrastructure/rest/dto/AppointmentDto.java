package com.example.turnos.reservation.infrastructure.rest.dto;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Una reserva de {@code GET /api/appointments} (REF §11). Solo los campos que se usan; el resto, que son
 * muchos (datos del paciente y del profesional), se ignora. El estado queda como texto: un valor nuevo de
 * la cátedra no tiene que hacer fallar la lectura de todas las demás.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppointmentDto {
	private Long reservationId;
	private String reservationProcessId;
	private String status;
	private Instant confirmedAt;
	private Instant cancelledAt;
	private String cancellationReason;
}
