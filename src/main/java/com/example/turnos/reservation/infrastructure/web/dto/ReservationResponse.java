package com.example.turnos.reservation.infrastructure.web.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// La representación de una reserva de docs/arq/contratos.md. Los identificadores de la cátedra
// (holdId, reservationProcessId) y los de los eventos no se exponen a la app.
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReservationResponse {
	private UUID id;
	private String status;
	private Long professionalId;
	private String professionalFirstName;
	private String professionalLastName;
	private LocalDate date;
	// Las respuestas van siempre con segundos (contratos.md).
	@JsonFormat(pattern = "HH:mm:ss")
	private LocalTime startTime;
	@JsonFormat(pattern = "HH:mm:ss")
	private LocalTime endTime;
	private Instant expiresAt;
	private String message;
	private String failureReason;
	private Instant createdAt;
	private Instant confirmedAt;
	private Instant cancelledAt;
	private String cancellationReason;
}
