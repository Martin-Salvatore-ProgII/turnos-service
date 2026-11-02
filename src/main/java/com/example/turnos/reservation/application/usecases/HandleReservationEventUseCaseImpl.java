package com.example.turnos.reservation.application.usecases;

import java.time.Clock;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.example.turnos.reservation.domain.model.EventHandlingResult;
import com.example.turnos.reservation.domain.model.ReservationEvent;
import com.example.turnos.reservation.domain.model.ReservationEventType;
import com.example.turnos.reservation.domain.model.ReservationProcess;
import com.example.turnos.reservation.domain.model.ReservationTrigger;
import com.example.turnos.reservation.domain.ports.in.HandleReservationEventUseCase;
import com.example.turnos.reservation.domain.ports.out.ProcessedEventRepository;
import com.example.turnos.reservation.domain.ports.out.ReservationProcessRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class HandleReservationEventUseCaseImpl implements HandleReservationEventUseCase {

	private final ReservationProcessRepository reservationProcessRepository;
	private final ProcessedEventRepository processedEventRepository;
	private final Clock clock;

	// Registrar el eventId y cambiar el proceso son una sola transacción. Si el cambio falla (la base,
	// o el proceso cambió en el medio, ADR-0068), el registro se deshace y la copia que Kafka reentregue
	// no se confunde con un duplicado ya procesado: de lo contrario el evento se perdería para siempre.
	@Override
	@Transactional
	public EventHandlingResult handleReservationEvent(ReservationEvent event) {
		ReservationProcess process = reservationProcessRepository
				.findByReservationProcessId(event.getReservationProcessId()).orElse(null);
		if (process == null) {
			// No se registra el eventId: no hay nada que deduplicar ni efecto que proteger (ADR-0031).
			log.warn("Event {} of type {} refers to unknown reservation process {}, ignored", event.getEventId(),
					event.getType(), event.getReservationProcessId());
			return EventHandlingResult.UNKNOWN_PROCESS;
		}
		if (!processedEventRepository.registerIfNew(event.getEventId(), clock.instant())) {
			// Queda a la vista que el duplicado se reconoció (ENUNCIADO §11, REF §18.1).
			log.info("Duplicate reservation event {} of process {} ignored", event.getEventId(),
					event.getReservationProcessId());
			return EventHandlingResult.DUPLICATE;
		}
		if (!process.apply(triggerOf(event.getType()))) {
			// Tardío o repetido con otro eventId: un estado final no se reabre (ADR-0023).
			log.info("Event {} of type {} has no transition from {} in process {}, ignored", event.getEventId(),
					event.getType(), process.getStatus(), event.getReservationProcessId());
			return EventHandlingResult.NO_TRANSITION;
		}
		copyDataOf(event, process);
		reservationProcessRepository.update(process.getId(), process)
				.orElseThrow(() -> new IllegalStateException("Reservation process " + process.getId() + " disappeared"));
		log.info("Event {} of type {} applied to process {}, now {}", event.getEventId(), event.getType(),
				event.getReservationProcessId(), process.getStatus());
		return EventHandlingResult.APPLIED;
	}

	// Lo que cada evento le agrega al proceso, una vez que la transición valió. Si no valió, el proceso
	// queda intacto: un evento tardío no pisa nada.
	private static void copyDataOf(ReservationEvent event, ReservationProcess process) {
		switch (event.getType()) {
			case INFORMATION_REQUESTED -> {
				// El eventId del pedido es el requestEventId que hay que devolver con el teléfono (REF §15.4).
				process.setRequestEventId(event.getEventId());
				process.setLastMessage(event.getMessage());
				if (event.getExpiresAt() != null) {
					process.setExpiresAt(event.getExpiresAt());
				}
			}
			case INFORMATION_REJECTED -> {
				// Vuelve a esperar el teléfono, con lo que la cátedra le dice al usuario y por qué (REF §15.7).
				// El requestEventId no cambia: el nuevo envío responde al mismo pedido, con un eventId nuevo.
				process.setLastMessage(event.getMessage());
				process.setFailureReason(event.getReason());
				if (event.getExpiresAt() != null) {
					process.setExpiresAt(event.getExpiresAt());
				}
			}
			default -> {
			}
		}
	}

	private static ReservationTrigger triggerOf(ReservationEventType type) {
		return switch (type) {
			case INFORMATION_REQUESTED -> ReservationTrigger.INFORMATION_REQUESTED;
			case INFORMATION_REJECTED -> ReservationTrigger.INFORMATION_REJECTED;
			case APPOINTMENT_CONFIRMED -> ReservationTrigger.APPOINTMENT_CONFIRMED;
			case PROCESS_EXPIRED -> ReservationTrigger.PROCESS_EXPIRED;
			case PROCESS_INVALID -> ReservationTrigger.PROCESS_INVALID;
		};
	}

}
