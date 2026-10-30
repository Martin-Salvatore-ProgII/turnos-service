package com.example.turnos.reservation.infrastructure.web.mapper;

import com.example.turnos.reservation.domain.model.ReservationProcess;
import com.example.turnos.reservation.infrastructure.web.dto.ReservationResponse;
import org.springframework.stereotype.Component;

@Component
public class ReservationDtoMapper {

	public ReservationResponse toResponse(ReservationProcess process) {
		if (process == null) {
			return null;
		}
		return ReservationResponse.builder()
				.id(process.getId())
				.status(process.getStatus() == null ? null : process.getStatus().name())
				.professionalId(process.getProfessionalId())
				.professionalFirstName(process.getProfessionalFirstName())
				.professionalLastName(process.getProfessionalLastName())
				.date(process.getAppointmentDate())
				.startTime(process.getStartTime())
				.endTime(process.getEndTime())
				.expiresAt(process.getExpiresAt())
				// En la API se llama message: es el último mensaje de la cátedra para el usuario.
				.message(process.getLastMessage())
				.failureReason(process.getFailureReason())
				.createdAt(process.getCreatedAt())
				.confirmedAt(process.getConfirmedAt())
				.cancelledAt(process.getCancelledAt())
				.cancellationReason(process.getCancellationReason())
				.build();
	}

}
