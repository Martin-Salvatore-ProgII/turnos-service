package com.example.turnos.reservation.domain.ports.in;

import com.example.turnos.reservation.domain.model.EventHandlingResult;
import com.example.turnos.reservation.domain.model.ReservationEvent;

public interface HandleReservationEventUseCase {

	// Aplica un evento de la cátedra al proceso al que pertenece. Nunca lanza por un evento que no se
	// puede aplicar (duplicado, de un proceso desconocido o sin transición): lo informa en el resultado.
	// Una falla de la base sí sube, para que quien consume reintente (ADR-0031).
	EventHandlingResult handleReservationEvent(ReservationEvent event);

}
