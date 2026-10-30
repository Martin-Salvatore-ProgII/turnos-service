package com.example.turnos.reservation.infrastructure.persistence.mapper;

import com.example.turnos.reservation.domain.model.ReservationProcess;
import com.example.turnos.reservation.infrastructure.persistence.entity.ReservationProcessEntity;
import org.springframework.stereotype.Component;

@Component
public class ReservationProcessMapper {

	public ReservationProcessEntity toEntity(ReservationProcess process) {
		if (process == null) {
			return null;
		}
		return ReservationProcessEntity.builder()
				.id(process.getId())
				.userId(process.getUserId())
				.status(process.getStatus())
				.professionalId(process.getProfessionalId())
				.professionalFirstName(process.getProfessionalFirstName())
				.professionalLastName(process.getProfessionalLastName())
				.appointmentDate(process.getAppointmentDate())
				.startTime(process.getStartTime())
				.endTime(process.getEndTime())
				.holdId(process.getHoldId())
				.reservationProcessId(process.getReservationProcessId())
				.reservationId(process.getReservationId())
				.expiresAt(process.getExpiresAt())
				.requestEventId(process.getRequestEventId())
				.submittedEventId(process.getSubmittedEventId())
				.lastMessage(process.getLastMessage())
				.failureReason(process.getFailureReason())
				.cancellationReason(process.getCancellationReason())
				.cancelledBy(process.getCancelledBy())
				.createdAt(process.getCreatedAt())
				.updatedAt(process.getUpdatedAt())
				.confirmedAt(process.getConfirmedAt())
				.cancelledAt(process.getCancelledAt())
				.version(process.getVersion())
				.build();
	}

	public ReservationProcess toDomainModel(ReservationProcessEntity entity) {
		if (entity == null) {
			return null;
		}
		return ReservationProcess.builder()
				.id(entity.getId())
				.userId(entity.getUserId())
				.status(entity.getStatus())
				.professionalId(entity.getProfessionalId())
				.professionalFirstName(entity.getProfessionalFirstName())
				.professionalLastName(entity.getProfessionalLastName())
				.appointmentDate(entity.getAppointmentDate())
				.startTime(entity.getStartTime())
				.endTime(entity.getEndTime())
				.holdId(entity.getHoldId())
				.reservationProcessId(entity.getReservationProcessId())
				.reservationId(entity.getReservationId())
				.expiresAt(entity.getExpiresAt())
				.requestEventId(entity.getRequestEventId())
				.submittedEventId(entity.getSubmittedEventId())
				.lastMessage(entity.getLastMessage())
				.failureReason(entity.getFailureReason())
				.cancellationReason(entity.getCancellationReason())
				.cancelledBy(entity.getCancelledBy())
				.createdAt(entity.getCreatedAt())
				.updatedAt(entity.getUpdatedAt())
				.confirmedAt(entity.getConfirmedAt())
				.cancelledAt(entity.getCancelledAt())
				.version(entity.getVersion())
				.build();
	}

}
