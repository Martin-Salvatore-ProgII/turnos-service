package com.example.turnos.reservation.application.service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import com.example.turnos.reservation.application.exception.ReservationException;
import com.example.turnos.reservation.domain.model.ReservationProcess;
import com.example.turnos.reservation.domain.ports.in.GetReservationUseCase;
import com.example.turnos.reservation.domain.ports.in.StartReservationUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ReservationService {

	private final StartReservationUseCase startReservationUseCase;
	private final GetReservationUseCase getReservationUseCase;

	// Sin Optional que traducir: el caso de uso informa cada rechazo con su código (ADR-0059).
	public ReservationProcess startReservation(String login, Long professionalId, LocalDate date, LocalTime startTime) {
		return startReservationUseCase.startReservation(login, professionalId, date, startTime);
	}

	// Un solo motivo de rechazo: el vacío del caso de uso es RESERVATION_NOT_FOUND, sea inexistente o
	// de otro usuario (ADR-0059, contratos.md).
	public ReservationProcess getReservation(String login, UUID id) {
		return getReservationUseCase.getReservation(login, id)
				.orElseThrow(() -> new ReservationException(ReservationException.Code.RESERVATION_NOT_FOUND,
						"Could not find reservation with id: " + id));
	}

}
