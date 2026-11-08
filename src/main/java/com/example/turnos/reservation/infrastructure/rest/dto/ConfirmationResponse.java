package com.example.turnos.reservation.infrastructure.rest.dto;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Respuesta 202 de la confirmación inicial (REF §10): status WAITING_FOR_PHONE.
@JsonIgnoreProperties(ignoreUnknown = true)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConfirmationResponse {
	private String reservationProcessId;
	private String status;
	private Instant expiresAt;
}
