package com.example.turnos.reservation.domain.ports.out;

import com.example.turnos.reservation.domain.model.PhoneSubmission;

public interface AdditionalInformationPublisherPort {

	// Publica el teléfono para la cátedra (REF §15.5) y devuelve true solo cuando el broker confirmó la
	// recepción. Repite de forma acotada con el mismo eventId (ADR-0029). Nunca lanza: si se agotan los
	// reintentos devuelve false, y la mensajería concreta queda detrás de este puerto.
	boolean publishPhone(PhoneSubmission submission);

}
