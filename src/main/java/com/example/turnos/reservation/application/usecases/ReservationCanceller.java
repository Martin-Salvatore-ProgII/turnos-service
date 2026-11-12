package com.example.turnos.reservation.application.usecases;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import com.example.turnos.reservation.application.exception.ReservationException;
import com.example.turnos.reservation.application.exception.ReservationProcessConflictException;
import com.example.turnos.reservation.domain.model.ReservationProcess;
import com.example.turnos.reservation.domain.model.ReservationStatus;
import com.example.turnos.reservation.domain.model.ReservationTrigger;
import com.example.turnos.reservation.domain.ports.out.AppointmentPort;
import com.example.turnos.reservation.domain.ports.out.ReservationProcessRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Las reglas de la cancelación de una reserva ya encontrada, compartidas por la cancelación del dueño y
 * la del administrador: el administrador cancela "con las mismas reglas de estado" (ADR-0037, ADR-0026).
 * No es un caso de uso ni tiene puerto: los dos casos de uso deciden qué reserva se cancela (la propia o
 * cualquiera) y quién queda registrado, y le pasan el resto a esta pieza.
 */
@Component
@RequiredArgsConstructor
public class ReservationCanceller {

	private final ReservationProcessRepository reservationProcessRepository;
	private final AppointmentPort appointmentPort;
	private final Clock clock;

	// Primero lo que se resuelve sin la cátedra (ya cancelada, estado, turno empezado) y recién después el
	// pedido. Sin transacción alrededor: no se tiene la base ocupada mientras se espera a la cátedra. Si la
	// cátedra rechaza o no responde, el adaptador lanza la excepción con su código y la reserva no cambia
	// (HU-09, criterio 5). cancelledBy es el login de quien canceló (arq/modelo-db.md).
	public ReservationProcess cancel(ReservationProcess process, String reason, String cancelledBy) {
		UUID id = process.getId();
		// Ya cancelada: éxito sin cambios y sin llamar a la cátedra, aunque el turno haya pasado (ADR-0026,
		// ADR-0071).
		if (process.getStatus() == ReservationStatus.CANCELLED) {
			return process;
		}
		if (process.getStatus() != ReservationStatus.CONFIRMED) {
			throw notCancellable(id, "is " + process.getStatus());
		}
		if (process.appointmentHasStarted(clock.instant())) {
			throw notCancellable(id, "has an appointment that already started");
		}

		Instant cancelledAt = appointmentPort.cancelAppointment(process.getReservationProcessId(), reason);

		markCancelled(process, cancelledAt, reason, cancelledBy);
		try {
			return save(process);
		} catch (ReservationProcessConflictException e) {
			return keepWhatWasDecided(process, cancelledAt, reason, cancelledBy);
		}
	}

	// Mientras se esperaba a la cátedra, el proceso cambió en la base: llegó AppointmentCancelled o la
	// reconciliación lo vio cancelado (ADR-0068). La cátedra ya lo canceló; se relee y solo se anota esta
	// cancelación si todavía tiene transición. Si ya está CANCELLED, queda lo que registró el otro camino.
	private ReservationProcess keepWhatWasDecided(ReservationProcess stale, Instant cancelledAt, String reason,
			String cancelledBy) {
		ReservationProcess latest = reservationProcessRepository.findById(stale.getId())
				.orElseThrow(() -> new IllegalStateException("Reservation process " + stale.getId() + " disappeared"));
		if (latest.getStatus() == ReservationStatus.CONFIRMED) {
			markCancelled(latest, cancelledAt, reason, cancelledBy);
			return save(latest);
		}
		return latest;
	}

	// El 200 de la cátedra es la confirmación autoritativa: con él la reserva pasa a CANCELLED, sin esperar
	// el evento (ADR-0026). Si la respuesta no trae el momento, vale el de ahora.
	private void markCancelled(ReservationProcess process, Instant cancelledAt, String reason, String cancelledBy) {
		process.apply(ReservationTrigger.APPOINTMENT_CANCELLED);
		process.setCancelledAt(cancelledAt != null ? cancelledAt : clock.instant());
		process.setCancellationReason(reason);
		process.setCancelledBy(cancelledBy);
	}

	private ReservationProcess save(ReservationProcess process) {
		return reservationProcessRepository.update(process.getId(), process)
				.orElseThrow(() -> new IllegalStateException("Reservation process " + process.getId() + " disappeared"));
	}

	private static ReservationException notCancellable(UUID id, String why) {
		return new ReservationException(ReservationException.Code.RESERVATION_NOT_CANCELLABLE,
				"Reservation " + id + " " + why + " and can not be cancelled");
	}

}
