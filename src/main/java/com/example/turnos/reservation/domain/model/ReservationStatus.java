package com.example.turnos.reservation.domain.model;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;

/**
 * Los estados de un proceso de reserva (docs/arq/maquina-estados.md). Cada uno responde a "¿qué
 * estamos esperando?".
 */
public enum ReservationStatus {
	STARTED(false),
	HELD(false),
	AWAITING_REQUEST(false),
	AWAITING_PHONE(false),
	PHONE_SUBMITTED(false),
	// Final, salvo por la cancelación (APPOINTMENT_CANCELLED).
	CONFIRMED(true),
	CANCELLED(true),
	EXPIRED(true),
	INVALID(true),
	FAILED(true);

	private final boolean finalStatus;

	ReservationStatus(boolean finalStatus) {
		this.finalStatus = finalStatus;
	}

	public boolean isFinal() {
		return finalStatus;
	}

	// Un proceso activo es uno no final: un usuario puede tener como máximo uno (ADR-0024).
	public static Set<ReservationStatus> active() {
		EnumSet<ReservationStatus> active = EnumSet.noneOf(ReservationStatus.class);
		Arrays.stream(values()).filter(status -> !status.isFinal()).forEach(active::add);
		return active;
	}

}
