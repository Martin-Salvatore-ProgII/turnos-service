package com.example.turnos.reservation.domain.ports.in;

import java.time.Duration;

public interface PurgeProcessedEventsUseCase {

	// Borra los eventos registrados hace más que la retención. Devuelve cuántos borró.
	int purgeProcessedEvents(Duration retention);

}
