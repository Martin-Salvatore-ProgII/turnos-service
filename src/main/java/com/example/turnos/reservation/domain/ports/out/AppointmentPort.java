package com.example.turnos.reservation.domain.ports.out;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.example.turnos.reservation.domain.model.CatedraAppointment;

public interface AppointmentPort {

	// Todas las reservas de ese paciente en la cátedra. La cuenta técnica es una sola para todo el
	// sistema, así que siempre se filtra por el externalPatientId del dueño (REF §11, ADR-0030). Si la
	// cátedra no responde o responde algo inesperado, el adaptador lanza ReservationException con
	// INTEGRATION_UNAVAILABLE: quien reconcilia deja el proceso como está para el próximo ciclo.
	List<CatedraAppointment> findAppointments(UUID externalPatientId);

	// Cancela la reserva en la cátedra, con el motivo si lo hay (REF §12). Es segura de repetir, porque la
	// cátedra trata como éxito cancelar algo ya cancelado, y se reintenta de forma acotada (ADR-0029).
	// Devuelve el momento de la cancelación que informa la cátedra, o null si no lo trae. Si la cátedra
	// responde RESERVATION_NOT_CANCELLABLE, lanza ReservationException con ese código; ante cualquier otra
	// falla, o sin respuesta después de los reintentos, con INTEGRATION_UNAVAILABLE.
	Instant cancelAppointment(String reservationProcessId, String reason);

}
