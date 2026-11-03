package com.example.turnos.reservation.infrastructure.kafka.mapper;

import org.springframework.stereotype.Component;

import com.example.turnos.reservation.domain.model.PhoneSubmission;
import com.example.turnos.reservation.infrastructure.kafka.dto.AdditionalInformationSubmittedMessage;

@Component
public class AdditionalInformationMessageMapper {

	static final String EVENT_TYPE = "AdditionalInformationSubmitted";
	static final int SCHEMA_VERSION = 1;

	// El groupId sale de la integración y no del usuario ni del pedido (REF §15.5, CLAUDE.md raíz).
	public AdditionalInformationSubmittedMessage toMessage(PhoneSubmission submission, String groupId) {
		if (submission == null) {
			return null;
		}
		return new AdditionalInformationSubmittedMessage(
				submission.getEventId(),
				EVENT_TYPE,
				groupId,
				submission.getReservationProcessId(),
				submission.getHoldId(),
				submission.getRequestEventId(),
				new AdditionalInformationSubmittedMessage.Information(submission.getPhoneNumber()),
				submission.getOccurredAt().toString(),
				SCHEMA_VERSION);
	}

}
