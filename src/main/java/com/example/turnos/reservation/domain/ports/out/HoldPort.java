package com.example.turnos.reservation.domain.ports.out;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import com.example.turnos.reservation.domain.model.HoldResult;

public interface HoldPort {

	// Pide a la cátedra un hold sobre el slot (REF §9). Nunca reintenta (ADR-0028) y nunca lanza: un
	// rechazo, un timeout o una cátedra caída vuelven como HoldResult.failed con su motivo.
	HoldResult createHold(Long professionalId, LocalDate date, LocalTime startTime, UUID externalPatientId);

}
