package com.example.turnos.reservation.application.usecases;

import java.time.Clock;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.example.turnos.reservation.application.exception.ReservationException;
import com.example.turnos.reservation.application.exception.ReservationProcessConflictException;
import com.example.turnos.reservation.domain.model.PhoneNumber;
import com.example.turnos.reservation.domain.model.PhoneSubmission;
import com.example.turnos.reservation.domain.model.ReservationProcess;
import com.example.turnos.reservation.domain.model.ReservationStatus;
import com.example.turnos.reservation.domain.model.ReservationTrigger;
import com.example.turnos.reservation.domain.ports.in.SubmitPhoneUseCase;
import com.example.turnos.reservation.domain.ports.out.AdditionalInformationPublisherPort;
import com.example.turnos.reservation.domain.ports.out.ReservationProcessRepository;
import com.example.turnos.user.domain.ports.out.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class SubmitPhoneUseCaseImpl implements SubmitPhoneUseCase {

	// El usuario es de otra feature y no tiene reglas propias sobre esta lectura: se usa su puerto de
	// salida (ADR-0062).
	private final UserRepository userRepository;
	private final ReservationProcessRepository reservationProcessRepository;
	private final AdditionalInformationPublisherPort publisherPort;
	private final Clock clock;

	// El orden es el de CU-04, paso 10: primero lo que se rechaza sin tocar nada (reserva ajena, estado,
	// formato) y recién después Kafka. No hay una transacción alrededor: el broker no participa de ella, y
	// el proceso solo pasa a PHONE_SUBMITTED cuando el broker confirmó (ADR-0029).
	@Override
	public ReservationProcess submitPhone(String login, UUID id, String phoneNumber) {
		ReservationProcess process = userRepository.findByLogin(login)
				.flatMap(user -> reservationProcessRepository.findByIdAndUserId(id, user.getId()))
				.orElseThrow(() -> new ReservationException(ReservationException.Code.RESERVATION_NOT_FOUND,
						"Could not find reservation with id: " + id));
		if (process.getStatus() != ReservationStatus.AWAITING_PHONE) {
			throw new ReservationException(ReservationException.Code.PHONE_NOT_REQUESTED,
					"Reservation " + id + " is " + process.getStatus() + " and is not waiting for a phone number");
		}
		String normalized = PhoneNumber.normalize(phoneNumber).orElseThrow(() -> new ReservationException(
				ReservationException.Code.INVALID_PHONE_NUMBER, "The phone number does not have a valid format"));
		if (process.getRequestEventId() == null) {
			// En AWAITING_PHONE siempre hay un pedido que responder: si falta, el dato local se perdió.
			throw new IllegalStateException("Reservation process " + id + " is waiting for a phone without a request");
		}

		// Un eventId nuevo por cada envío del usuario: tras un rechazo la cátedra espera otro (REF §15.7).
		// Los reintentos de este envío los hace el adaptador con el mismo (ADR-0029).
		String eventId = "phone-submitted-" + UUID.randomUUID();
		boolean published = publisherPort.publishPhone(PhoneSubmission.builder()
				.eventId(eventId)
				.reservationProcessId(process.getReservationProcessId())
				.holdId(process.getHoldId())
				.requestEventId(process.getRequestEventId())
				.phoneNumber(normalized)
				.occurredAt(clock.instant())
				.build());
		if (!published) {
			// El proceso sigue esperando el teléfono y el usuario puede volver a intentar mientras no venza.
			throw new ReservationException(ReservationException.Code.INTEGRATION_UNAVAILABLE,
					"The phone number could not be sent to the catedra");
		}

		process.apply(ReservationTrigger.INFORMATION_SUBMITTED);
		process.setSubmittedEventId(eventId);
		// El motivo de un rechazo anterior ya no vale: este envío todavía no tiene resultado.
		process.setFailureReason(null);
		try {
			return save(process);
		} catch (ReservationProcessConflictException e) {
			return keepWhatTheEventsDecided(process, eventId);
		}
	}

	// Mientras se publicaba, llegó un evento y cambió el proceso (por ejemplo, ya se confirmó). El teléfono
	// ya está publicado y el evento manda: se relee y solo se anota el envío si todavía corresponde.
	private ReservationProcess keepWhatTheEventsDecided(ReservationProcess stale, String eventId) {
		ReservationProcess latest = reservationProcessRepository.findById(stale.getId())
				.orElseThrow(() -> new IllegalStateException("Reservation process " + stale.getId() + " disappeared"));
		if (latest.apply(ReservationTrigger.INFORMATION_SUBMITTED)) {
			latest.setSubmittedEventId(eventId);
			latest.setFailureReason(null);
			return save(latest);
		}
		return latest;
	}

	private ReservationProcess save(ReservationProcess process) {
		return reservationProcessRepository.update(process.getId(), process)
				.orElseThrow(() -> new IllegalStateException("Reservation process " + process.getId() + " disappeared"));
	}

}
