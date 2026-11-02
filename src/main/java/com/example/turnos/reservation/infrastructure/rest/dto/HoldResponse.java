package com.example.turnos.reservation.infrastructure.rest.dto;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Respuesta 201 de POST /api/appointment-holds (REF §9).
@JsonIgnoreProperties(ignoreUnknown = true)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HoldResponse {
	private String holdId;
	private String reservationProcessId;
	private String status;
	private Instant expiresAt;
}
