package com.example.turnos.reservation.domain.ports.out;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import com.example.turnos.reservation.domain.model.ConfirmationResult;
import com.example.turnos.reservation.domain.model.HoldResult;

public interface HoldPort {

	// Pide a la cátedra un hold sobre el slot (REF §9). Nunca reintenta (ADR-0028) y nunca lanza: un
	// rechazo, un timeout o una cátedra caída vuelven como HoldResult.failed con su motivo.
	HoldResult createHold(Long professionalId, LocalDate date, LocalTime startTime, UUID externalPatientId);

	// Pide la confirmación inicial del hold con los datos del paciente (REF §10). Es segura de repetir y
	// se reintenta de forma acotada (ADR-0029). Nunca lanza: cada final vuelve como un ConfirmationResult.
	ConfirmationResult confirmHold(String holdId, UUID externalPatientId, String patientFirstName,
			String patientLastName);

}
