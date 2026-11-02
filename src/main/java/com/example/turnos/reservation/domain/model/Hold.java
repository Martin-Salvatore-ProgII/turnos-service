package com.example.turnos.reservation.domain.model;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// El hold que creó la cátedra (REF §9): retiene el slot hasta expiresAt.
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Hold {
	private String holdId;
	private String reservationProcessId;
	private Instant expiresAt;
}
