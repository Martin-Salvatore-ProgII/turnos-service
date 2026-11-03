package com.example.turnos.reservation.infrastructure.rest.mapper;

import org.springframework.stereotype.Component;

import com.example.turnos.reservation.domain.model.CatedraAppointment;
import com.example.turnos.reservation.domain.model.CatedraAppointmentStatus;
import com.example.turnos.reservation.infrastructure.rest.dto.AppointmentDto;
import com.example.turnos.reservation.infrastructure.rest.dto.CancellationRequest;

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

	// Sin motivo no hay cuerpo: la cátedra lo acepta así (REF §12, probado con probar-reserva-catedra.sh).
	public CancellationRequest toCancellationRequest(String reason) {
		if (reason == null) {
			return null;
		}
		return CancellationRequest.builder()
				.reason(reason)
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
