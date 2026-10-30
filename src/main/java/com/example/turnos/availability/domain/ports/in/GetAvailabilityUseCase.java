package com.example.turnos.availability.domain.ports.in;

import java.time.LocalDate;

import com.example.turnos.availability.domain.model.Availability;

public interface GetAvailabilityUseCase {

	// Tiene varios motivos de rechazo (fecha fuera de rango, profesional inexistente o deshabilitado):
	// los informa con AvailabilityException y su código (ADR-0059).
	Availability getAvailability(Long professionalId, LocalDate date);

}
