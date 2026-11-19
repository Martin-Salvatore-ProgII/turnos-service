package com.example.turnos.reservation.domain.ports.in;

import java.util.UUID;

import com.example.turnos.reservation.domain.model.ReservationWithOwner;

public interface CancelAnyReservationUseCase {

	// Cancela la reserva del id, de cualquier usuario, en nombre del administrador del login (el del JWT),
	// con un motivo obligatorio. Mismas reglas que la cancelación del dueño (HU-10, criterios 3 y 4;
	// ADR-0037). Devuelve la reserva en CANCELLED, con su dueño, también si ya lo estaba. Solo para el
	// administrador: lo garantiza la ruta, no este caso de uso.
	ReservationWithOwner cancelAnyReservation(String adminLogin, UUID id, String reason);

}
