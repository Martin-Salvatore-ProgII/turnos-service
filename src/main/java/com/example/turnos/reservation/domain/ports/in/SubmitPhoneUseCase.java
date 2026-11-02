package com.example.turnos.reservation.domain.ports.in;

import java.util.UUID;

import com.example.turnos.reservation.domain.model.ReservationProcess;

public interface SubmitPhoneUseCase {

	// Envía el teléfono de una reserva propia que lo está esperando (CU-04, 10). Cada rechazo lanza
	// ReservationException con su código (ADR-0059): reserva inexistente o ajena, estado que no espera el
	// teléfono, formato inválido o cátedra sin respuesta. Devuelve la reserva en PHONE_SUBMITTED.
	ReservationProcess submitPhone(String login, UUID id, String phoneNumber);

}
