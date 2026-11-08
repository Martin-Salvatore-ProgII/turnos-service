package com.example.turnos.reservation.application.usecases;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import com.example.turnos.reservation.application.exception.ReservationException;
import com.example.turnos.reservation.application.exception.ReservationProcessConflictException;
import com.example.turnos.reservation.domain.model.ReservationProcess;
import com.example.turnos.reservation.domain.model.ReservationStatus;
import com.example.turnos.reservation.domain.model.ReservationTrigger;
import com.example.turnos.reservation.domain.ports.in.CancelReservationUseCase;
import com.example.turnos.reservation.domain.ports.out.AppointmentPort;
import com.example.turnos.reservation.domain.ports.out.ReservationProcessRepository;
import com.example.turnos.user.domain.ports.out.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CancelReservationUseCaseImpl implements CancelReservationUseCase {

	private final UserRepository userRepository;
	private final ReservationProcessRepository reservationProcessRepository;
	private final AppointmentPort appointmentPort;
	private final Clock clock;

	// Primero lo que se resuelve sin la cátedra (reserva ajena, ya cancelada, estado, turno empezado) y
	// recién después el pedido. Sin transacción alrededor, como al iniciar: no se tiene la base ocupada
	// mientras se espera a la cátedra. Si la cátedra rechaza o no responde, el adaptador lanza la
	// excepción con su código y la reserva no cambia (HU-09, criterio 5).
	@Override
	public ReservationProcess cancelReservation(String login, UUID id, String reason) {
		ReservationProcess process = userRepository.findByLogin(login)
				.flatMap(user -> reservationProcessRepository.findByIdAndUserId(id, user.getId()))
				.orElseThrow(() -> new ReservationException(ReservationException.Code.RESERVATION_NOT_FOUND,
						"Could not find reservation with id: " + id));
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

		markCancelled(process, cancelledAt, reason, login);
		try {
			return save(process);
		} catch (ReservationProcessConflictException e) {
			return keepWhatWasDecided(process, cancelledAt, reason, login);
		}
	}

	// Mientras se esperaba a la cátedra, el proceso cambió en la base: llegó AppointmentCancelled o la
	// reconciliación lo vio cancelado (ADR-0068). La cátedra ya lo canceló; se relee y solo se anota esta
	// cancelación si todavía tiene transición. Si ya está CANCELLED, queda lo que registró el otro camino.
	private ReservationProcess keepWhatWasDecided(ReservationProcess stale, Instant cancelledAt, String reason,
			String login) {
		ReservationProcess latest = reservationProcessRepository.findById(stale.getId())
				.orElseThrow(() -> new IllegalStateException("Reservation process " + stale.getId() + " disappeared"));
		if (latest.getStatus() == ReservationStatus.CONFIRMED) {
			markCancelled(latest, cancelledAt, reason, login);
			return save(latest);
		}
		return latest;
	}

	// El 200 de la cátedra es la confirmación autoritativa: con él la reserva pasa a CANCELLED, sin esperar
	// el evento (ADR-0026). Si la respuesta no trae el momento, vale el de ahora.
	private void markCancelled(ReservationProcess process, Instant cancelledAt, String reason, String login) {
		process.apply(ReservationTrigger.APPOINTMENT_CANCELLED);
		process.setCancelledAt(cancelledAt != null ? cancelledAt : clock.instant());
		process.setCancellationReason(reason);
		// Quién canceló: el login del dueño (arq/modelo-db.md).
		process.setCancelledBy(login);
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
