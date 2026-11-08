package com.example.turnos.reservation.domain.ports.out;

import java.util.List;
import java.util.UUID;

import com.example.turnos.reservation.domain.model.CatedraAppointment;

public interface AppointmentPort {

	// Todas las reservas de ese paciente en la cátedra. La cuenta técnica es una sola para todo el
	// sistema, así que siempre se filtra por el externalPatientId del dueño (REF §11, ADR-0030). Si la
	// cátedra no responde o responde algo inesperado, el adaptador lanza ReservationException con
	// INTEGRATION_UNAVAILABLE: quien reconcilia deja el proceso como está para el próximo ciclo.
	List<CatedraAppointment> findAppointments(UUID externalPatientId);

}
