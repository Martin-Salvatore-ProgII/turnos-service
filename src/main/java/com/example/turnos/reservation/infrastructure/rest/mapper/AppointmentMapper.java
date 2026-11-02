package com.example.turnos.reservation.infrastructure.rest.mapper;

import org.springframework.stereotype.Component;

import com.example.turnos.reservation.domain.model.CatedraAppointment;
import com.example.turnos.reservation.domain.model.CatedraAppointmentStatus;
import com.example.turnos.reservation.infrastructure.rest.dto.AppointmentDto;

@Component
public class AppointmentMapper {

	public CatedraAppointment toDomainModel(AppointmentDto dto) {
		if (dto == null) {
			return null;
		}
		return CatedraAppointment.builder()
				.reservationProcessId(dto.getReservationProcessId())
				.reservationId(dto.getReservationId())
				.status(statusOf(dto.getStatus()))
				.confirmedAt(dto.getConfirmedAt())
				.cancelledAt(dto.getCancelledAt())
				.cancellationReason(dto.getCancellationReason())
				.build();
	}

	// Un estado que no se conoce no es un error: la reconciliación lo deja para el próximo ciclo.
	private static CatedraAppointmentStatus statusOf(String status) {
		if (status == null) {
			return CatedraAppointmentStatus.UNKNOWN;
		}
		try {
			return CatedraAppointmentStatus.valueOf(status);
		} catch (IllegalArgumentException e) {
			return CatedraAppointmentStatus.UNKNOWN;
		}
	}

}
