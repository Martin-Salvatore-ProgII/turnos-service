package com.example.turnos.reservation.domain.model;

/** Qué pasó con un evento de la cátedra al procesarlo. Sirve para registrarlo y para las pruebas. */
public enum EventHandlingResult {
	// Cambió el proceso.
	APPLIED,
	// El eventId ya estaba registrado: no se repiten efectos (ADR-0008).
	DUPLICATE,
	// No hay un proceso local con ese reservationProcessId (ADR-0031).
	UNKNOWN_PROCESS,
	// El evento no tiene transición desde el estado actual: tardío o repetido (ADR-0023).
	NO_TRANSITION
}
