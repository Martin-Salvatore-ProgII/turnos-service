package com.example.turnos.availability.domain.model;

import java.time.ZoneId;

// La zona horaria de la agenda: fija y no configurable (ADR-0016). Con ella se sabe qué fecha es
// "hoy" y qué hora es "ahora", sin depender de la zona del servidor.
public final class AgendaZone {

	public static final ZoneId ZONE = ZoneId.of("America/Argentina/Buenos_Aires");

	private AgendaZone() {
	}

}
