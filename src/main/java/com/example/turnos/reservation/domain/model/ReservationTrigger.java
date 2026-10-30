package com.example.turnos.reservation.domain.model;

import static com.example.turnos.reservation.domain.model.ReservationStatus.AWAITING_PHONE;
import static com.example.turnos.reservation.domain.model.ReservationStatus.AWAITING_REQUEST;
import static com.example.turnos.reservation.domain.model.ReservationStatus.CANCELLED;
import static com.example.turnos.reservation.domain.model.ReservationStatus.CONFIRMED;
import static com.example.turnos.reservation.domain.model.ReservationStatus.EXPIRED;
import static com.example.turnos.reservation.domain.model.ReservationStatus.FAILED;
import static com.example.turnos.reservation.domain.model.ReservationStatus.HELD;
import static com.example.turnos.reservation.domain.model.ReservationStatus.INVALID;
import static com.example.turnos.reservation.domain.model.ReservationStatus.PHONE_SUBMITTED;
import static com.example.turnos.reservation.domain.model.ReservationStatus.STARTED;

import java.util.EnumSet;
import java.util.Set;

/**
 * Lo que puede hacer cambiar de estado a un proceso de reserva: una respuesta REST de la cátedra,
 * un evento Kafka o la reconciliación. Cada constante es una fila de la tabla de transiciones de
 * docs/arq/maquina-estados.md: el estado al que lleva y los únicos estados desde los que vale.
 *
 * <p>Desde cualquier otro estado, el disparador no cambia nada (ADR-0023). Así un evento tardío o
 * repetido nunca hace retroceder un proceso ni lo saca de un estado final.
 */
public enum ReservationTrigger {

	// La cátedra crea el hold (201).
	HOLD_CREATED(HELD, EnumSet.of(STARTED)),
	// La cátedra rechaza el hold, el pedido no tiene respuesta (ADR-0028) o la reconciliación
	// encuentra el proceso sin hold registrado (ADR-0030).
	HOLD_FAILED(FAILED, EnumSet.of(STARTED)),
	// La cátedra acepta la confirmación inicial (202).
	CONFIRMATION_ACCEPTED(AWAITING_REQUEST, EnumSet.of(HELD)),
	// La confirmación inicial responde HOLD_EXPIRED.
	CONFIRMATION_EXPIRED(EXPIRED, EnumSet.of(HELD)),
	// La confirmación inicial se rechaza por cualquier otro motivo.
	CONFIRMATION_REJECTED(FAILED, EnumSet.of(HELD)),
	// Llega AdditionalInformationRequested. Puede llegar antes que el 202 de la confirmación, por eso
	// también vale desde HELD; el 202 que llega después ya no tiene transición (CU-04, 8a).
	INFORMATION_REQUESTED(AWAITING_PHONE, EnumSet.of(HELD, AWAITING_REQUEST)),
	// Se publica AdditionalInformationSubmitted.
	INFORMATION_SUBMITTED(PHONE_SUBMITTED, EnumSet.of(AWAITING_PHONE)),
	// Llega AdditionalInformationRejected: se vuelve a esperar un teléfono, no es un estado aparte.
	INFORMATION_REJECTED(AWAITING_PHONE, EnumSet.of(PHONE_SUBMITTED)),
	// Los tres eventos finales de la cátedra valen desde cualquier estado no final: la cátedra es la
	// autoridad aunque el estado local todavía no haya registrado los pasos anteriores.
	APPOINTMENT_CONFIRMED(CONFIRMED, ReservationStatus.active()),
	PROCESS_EXPIRED(EXPIRED, ReservationStatus.active()),
	PROCESS_INVALID(INVALID, ReservationStatus.active()),
	// La cancelación aceptada (200) o AppointmentCancelled: la única salida de un estado final.
	APPOINTMENT_CANCELLED(CANCELLED, EnumSet.of(CONFIRMED)),
	// Reconciliación: venció expiresAt sin teléfono enviado, así que la cátedra no pudo confirmar. No
	// vale desde PHONE_SUBMITTED: ese caso se consulta a la cátedra (ADR-0030).
	EXPIRED_WITHOUT_PHONE(EXPIRED, EnumSet.of(HELD, AWAITING_REQUEST, AWAITING_PHONE)),
	// Reconciliación: con el teléfono enviado, la cátedra informa la reserva como FAILED (ADR-0030).
	CATEDRA_REPORTED_FAILED(EXPIRED, EnumSet.of(PHONE_SUBMITTED));

	private final ReservationStatus target;
	private final Set<ReservationStatus> sources;

	ReservationTrigger(ReservationStatus target, Set<ReservationStatus> sources) {
		this.target = target;
		this.sources = sources;
	}

	public ReservationStatus getTarget() {
		return target;
	}

	public boolean appliesTo(ReservationStatus status) {
		return sources.contains(status);
	}

}
