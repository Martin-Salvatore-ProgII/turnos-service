package com.example.turnos.availability.domain.ports.out;

import java.time.LocalDate;
import java.util.List;

import com.example.turnos.availability.domain.model.Occupancy;

public interface OccupancyPort {

	// Las ocupaciones del profesional en esa fecha. La disponibilidad es de una fecha por vez, así que
	// se pide un solo día (ADR-0015). Una lista vacía es válida. Si la cátedra no responde o responde
	// algo inesperado, el adaptador lanza IntegrationUnavailableException.
	List<Occupancy> getOccupancies(Long professionalId, LocalDate date);

}
