package com.example.turnos.reservation.domain.ports.out;

import java.time.Instant;

public interface ProcessedEventRepository {

	// Registra el evento si es la primera vez que se ve. Devuelve false si ya estaba registrado. Lo
	// decide la base con la clave primaria, no una consulta previa: dos copias del mismo evento que
	// llegan a la vez no pueden registrarse las dos (ADR-0070).
	boolean registerIfNew(String eventId, Instant processedAt);

	// Borra los eventos registrados antes de la fecha. Devuelve cuántos borró.
	int deleteProcessedBefore(Instant cutoff);

}
