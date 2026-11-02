package com.example.turnos.reservation.domain.model;

/** El estado de una reserva según la cátedra (REF §11). {@code UNKNOWN} es cualquier valor que este servicio no conoce. */
public enum CatedraAppointmentStatus {
	PHONE_PENDING,
	CONFIRMED,
	CANCELLED,
	FAILED,
	UNKNOWN
}
