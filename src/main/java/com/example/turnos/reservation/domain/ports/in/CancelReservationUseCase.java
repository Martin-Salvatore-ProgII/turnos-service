package com.example.turnos.reservation.domain.ports.in;

import java.util.UUID;

import com.example.turnos.reservation.domain.model.ReservationProcess;

public interface CancelReservationUseCase {

	// Cancela la reserva del id, solo si es del usuario del login (el del JWT), con un motivo opcional
	// (null si no lo hay). Devuelve la reserva en CANCELLED, también si ya lo estaba (HU-09, ADR-0026).
	ReservationProcess cancelReservation(String login, UUID id, String reason);

}
