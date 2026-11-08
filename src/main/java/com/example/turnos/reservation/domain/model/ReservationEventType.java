package com.example.turnos.reservation.domain.model;

/**
 * Los eventos del topic de acciones que el servicio sabe procesar (REF §15.4, §15.6 a §15.9). Un
 * {@code eventType} que no está acá se ignora (ADR-0031).
 */
public enum ReservationEventType {
	INFORMATION_REQUESTED,
	INFORMATION_REJECTED,
	APPOINTMENT_CONFIRMED,
	PROCESS_EXPIRED,
	PROCESS_INVALID
}
