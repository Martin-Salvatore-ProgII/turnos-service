package com.example.turnos.reservation.infrastructure.rest.mapper;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import com.example.turnos.reservation.domain.model.Hold;
import com.example.turnos.reservation.infrastructure.rest.dto.ConfirmationRequest;
import com.example.turnos.reservation.infrastructure.rest.dto.HoldRequest;
import com.example.turnos.reservation.infrastructure.rest.dto.HoldResponse;
import org.springframework.stereotype.Component;

@Component
public class HoldMapper {

	public HoldRequest toRequest(Long professionalId, LocalDate date, LocalTime startTime, UUID externalPatientId) {
		return HoldRequest.builder()
				.professionalId(professionalId)
				.date(date)
				.startTime(startTime)
				.externalPatientId(externalPatientId == null ? null : externalPatientId.toString())
				.build();
	}

	public ConfirmationRequest toConfirmationRequest(UUID externalPatientId, String patientFirstName,
			String patientLastName) {
		return ConfirmationRequest.builder()
				.externalPatientId(externalPatientId == null ? null : externalPatientId.toString())
				.patientFirstName(patientFirstName)
				.patientLastName(patientLastName)
				.build();
	}

	public Hold toDomainModel(HoldResponse response) {
		if (response == null) {
			return null;
		}
		return Hold.builder()
				.holdId(response.getHoldId())
				.reservationProcessId(response.getReservationProcessId())
				.expiresAt(response.getExpiresAt())
				.build();
	}

}
