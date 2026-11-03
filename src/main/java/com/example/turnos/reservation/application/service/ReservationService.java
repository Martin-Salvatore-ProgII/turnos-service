package com.example.turnos.reservation.application.service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import com.example.turnos.reservation.application.exception.ReservationException;
import com.example.turnos.reservation.domain.model.EventHandlingResult;
import com.example.turnos.reservation.domain.model.PageQuery;
import com.example.turnos.reservation.domain.model.PageResult;
import com.example.turnos.reservation.domain.model.ReservationEvent;
import com.example.turnos.reservation.domain.model.ReservationProcess;
import com.example.turnos.reservation.domain.model.ReservationStatus;
import com.example.turnos.reservation.domain.ports.in.CancelReservationUseCase;
import com.example.turnos.reservation.domain.ports.in.GetReservationUseCase;
import com.example.turnos.reservation.domain.ports.in.HandleReservationEventUseCase;
import com.example.turnos.reservation.domain.ports.in.ListReservationsUseCase;
import com.example.turnos.reservation.domain.ports.in.PurgeProcessedEventsUseCase;
import com.example.turnos.reservation.domain.ports.in.ReconcileReservationProcessesUseCase;
import com.example.turnos.reservation.domain.ports.in.StartReservationUseCase;
import com.example.turnos.reservation.domain.ports.in.SubmitPhoneUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ReservationService {

	private final StartReservationUseCase startReservationUseCase;
	private final GetReservationUseCase getReservationUseCase;
	private final ListReservationsUseCase listReservationsUseCase;
	private final HandleReservationEventUseCase handleReservationEventUseCase;
	private final SubmitPhoneUseCase submitPhoneUseCase;
	private final CancelReservationUseCase cancelReservationUseCase;
	private final ReconcileReservationProcessesUseCase reconcileReservationProcessesUseCase;
	private final PurgeProcessedEventsUseCase purgeProcessedEventsUseCase;

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

	// Sin rechazos propios: un usuario sin reservas recibe una página vacía.
	public PageResult<ReservationProcess> listReservations(String login, ReservationStatus status, PageQuery page) {
		return listReservationsUseCase.listReservations(login, status, page);
	}

	// Como iniciar: el caso de uso informa cada rechazo con su código (ADR-0059).
	public ReservationProcess submitPhone(String login, UUID id, String phoneNumber) {
		return submitPhoneUseCase.submitPhone(login, id, phoneNumber);
	}

	// Como iniciar: el caso de uso informa cada rechazo con su código (ADR-0059).
	public ReservationProcess cancelReservation(String login, UUID id, String reason) {
		return cancelReservationUseCase.cancelReservation(login, id, reason);
	}

	// Las dos las llama el scheduler de la reconciliación (ADR-0030).
	public int reconcileReservationProcesses(Duration margin) {
		return reconcileReservationProcessesUseCase.reconcileReservationProcesses(margin);
	}

	public int purgeProcessedEvents(Duration retention) {
		return purgeProcessedEventsUseCase.purgeProcessedEvents(retention);
	}

	// Lo llama el consumidor de Kafka. No hay Optional que traducir: el resultado dice qué pasó con el
	// evento y ningún resultado es un error.
	public EventHandlingResult handleReservationEvent(ReservationEvent event) {
		return handleReservationEventUseCase.handleReservationEvent(event);
	}

}
