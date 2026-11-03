package com.example.turnos.reservation.infrastructure.rest.dto;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Respuesta 200 de la cancelación (REF §12): status CANCELLED, también si ya estaba cancelada.
@JsonIgnoreProperties(ignoreUnknown = true)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CancellationResponse {
	private Long reservationId;
	private String reservationProcessId;
	private String status;
	private Instant cancelledAt;
}
