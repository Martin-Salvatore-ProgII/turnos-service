package com.example.turnos.availability.application.service;

import java.time.LocalDate;

import com.example.turnos.availability.domain.model.Availability;
import com.example.turnos.availability.domain.ports.in.GetAvailabilityUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AvailabilityService {

	private final GetAvailabilityUseCase getAvailabilityUseCase;

	// Sin Optional que traducir: el caso de uso ya informa cada rechazo con su código (ADR-0059).
	public Availability getAvailability(Long professionalId, LocalDate date) {
		return getAvailabilityUseCase.getAvailability(professionalId, date);
	}

}
