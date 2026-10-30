package com.example.turnos.reservation.application.usecases;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.UUID;

import com.example.turnos.availability.domain.model.AgendaZone;
import com.example.turnos.availability.domain.model.AvailabilityHorizon;
import com.example.turnos.availability.domain.model.ScheduleSlots;
import com.example.turnos.availability.domain.model.Slot;
import com.example.turnos.catalog.domain.model.ProfessionalAgenda;
import com.example.turnos.catalog.domain.ports.out.CatalogClientPort;
import com.example.turnos.reservation.application.exception.ReservationException;
import com.example.turnos.reservation.domain.model.ConfirmationResult;
import com.example.turnos.reservation.domain.model.HoldResult;
import com.example.turnos.reservation.domain.model.ReservationProcess;
import com.example.turnos.reservation.domain.model.ReservationStatus;
import com.example.turnos.reservation.domain.model.ReservationTrigger;
import com.example.turnos.reservation.domain.ports.in.StartReservationUseCase;
import com.example.turnos.reservation.domain.ports.out.HoldPort;
import com.example.turnos.reservation.domain.ports.out.ReservationProcessRepository;
import com.example.turnos.user.domain.model.User;
import com.example.turnos.user.domain.ports.out.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StartReservationUseCaseImpl implements StartReservationUseCase {

	// El usuario y la agenda son de otras features sin reglas propias sobre estas lecturas: se usan sus
	// puertos de salida (ADR-0062).
	private final UserRepository userRepository;
	private final CatalogClientPort catalogClientPort;
	private final ReservationProcessRepository reservationProcessRepository;
	private final HoldPort holdPort;
	private final AvailabilityHorizon availabilityHorizon;
	private final Clock clock;

	// El orden es el de CU-04, pasos 3 a 6: todo lo que se puede rechazar sin la cátedra se rechaza
	// antes de guardar nada. Si el catálogo no responde, su excepción sigue de largo y no se inicia la
	// reserva (3a).
	// No hay una transacción alrededor: cada guardado se confirma solo, para que el proceso quede en la
	// base antes de llamar a la cátedra y no se tenga la base ocupada mientras se espera la respuesta.
	@Override
	public ReservationProcess startReservation(String login, Long professionalId, LocalDate date, LocalTime startTime) {
		ZonedDateTime now = ZonedDateTime.now(clock.withZone(AgendaZone.ZONE));
		if (!availabilityHorizon.includes(date, now.toLocalDate())) {
			throw new ReservationException(ReservationException.Code.DATE_OUT_OF_RANGE, "Date " + date
					+ " must be between " + now.toLocalDate() + " and " + availabilityHorizon.lastDay(now.toLocalDate()));
		}
		// El login sale del JWT ya validado: si no existe, es un error interno, no del usuario.
		User user = userRepository.findByLogin(login)
				.orElseThrow(() -> new IllegalStateException("Authenticated user not found"));

		ProfessionalAgenda agenda = catalogClientPort.getProfessionalAgenda(professionalId)
				.orElseThrow(() -> new ReservationException(ReservationException.Code.PROFESSIONAL_NOT_FOUND,
						"Could not find professional with id: " + professionalId));
		if (!agenda.isEnabled()) {
			throw new ReservationException(ReservationException.Code.PROFESSIONAL_DISABLED,
					"Professional " + professionalId + " is disabled");
		}
		Slot slot = findSlot(agenda, date, startTime, now);

		ReservationProcess process = reservationProcessRepository.create(ReservationProcess.builder()
				.id(UUID.randomUUID())
				.userId(user.getId())
				.status(ReservationStatus.STARTED)
				.professionalId(agenda.getId())
				.professionalFirstName(agenda.getFirstName())
				.professionalLastName(agenda.getLastName())
				.appointmentDate(date)
				.startTime(slot.getStartTime())
				.endTime(slot.getEndTime())
				.build());
		ReservationProcess held = requestHold(process, user);
		if (held.getStatus() != ReservationStatus.HELD) {
			return held;
		}
		return confirmHold(held, user);
	}

	// El slot tiene que salir de la agenda vigente para ese día, con la misma regla que la
	// disponibilidad (ADR-0015). Para hoy, uno que ya empezó tampoco vale: se rechaza acá, sin
	// preguntarle a la cátedra, igual que la disponibilidad no lo ofrece (ADR-0016).
	private Slot findSlot(ProfessionalAgenda agenda, LocalDate date, LocalTime startTime, ZonedDateTime now) {
		boolean today = date.equals(now.toLocalDate());
		return ScheduleSlots.ofDay(agenda, date).stream()
				.filter(slot -> slot.getStartTime().equals(startTime))
				.filter(slot -> !today || !slot.getStartTime().isBefore(now.toLocalTime()))
				.findFirst()
				.orElseThrow(() -> new ReservationException(ReservationException.Code.INVALID_SLOT,
						"Slot " + date + " " + startTime + " is not available in the agenda of professional "
								+ agenda.getId()));
	}

	// Con el hold creado se guardan sus identificadores y pasa a HELD antes de seguir (ADR-0022). Si no
	// se creó, por un rechazo o porque la cátedra no respondió, el proceso termina en FAILED con el
	// motivo y el usuario puede volver a intentar (CU-04 5a y 5b, ADR-0028, ADR-0069).
	private ReservationProcess requestHold(ReservationProcess process, User user) {
		HoldResult result = holdPort.createHold(process.getProfessionalId(), process.getAppointmentDate(),
				process.getStartTime(), user.getExternalPatientId());
		if (result.isCreated()) {
			process.apply(ReservationTrigger.HOLD_CREATED);
			process.setHoldId(result.getHold().getHoldId());
			process.setReservationProcessId(result.getHold().getReservationProcessId());
			process.setExpiresAt(result.getHold().getExpiresAt());
		} else {
			process.apply(ReservationTrigger.HOLD_FAILED);
			process.setFailureReason(result.getFailureReason());
		}
		return save(process);
	}

	// La confirmación no necesita nada del usuario: va enseguida del hold (ADR-0019), con los datos del
	// paciente que ya tiene el servicio. Si no hubo respuesta, el proceso queda en HELD sin cambios: la
	// cátedra pudo haberla recibido y no se puede cerrar como fallido (ADR-0029).
	private ReservationProcess confirmHold(ReservationProcess process, User user) {
		ConfirmationResult result = holdPort.confirmHold(process.getHoldId(), user.getExternalPatientId(),
				user.getFirstName(), user.getLastName());
		switch (result.getOutcome()) {
			case ACCEPTED -> {
				process.apply(ReservationTrigger.CONFIRMATION_ACCEPTED);
				// Una confirmación repetida no trae el vencimiento: sigue valiendo el del hold.
				if (result.getExpiresAt() != null) {
					process.setExpiresAt(result.getExpiresAt());
				}
			}
			case EXPIRED -> {
				process.apply(ReservationTrigger.CONFIRMATION_EXPIRED);
				process.setFailureReason(result.getFailureReason());
			}
			case REJECTED -> {
				process.apply(ReservationTrigger.CONFIRMATION_REJECTED);
				process.setFailureReason(result.getFailureReason());
			}
			case NO_RESPONSE -> {
				return process;
			}
		}
		return save(process);
	}

	private ReservationProcess save(ReservationProcess process) {
		return reservationProcessRepository.update(process.getId(), process)
				.orElseThrow(() -> new IllegalStateException("Reservation process " + process.getId() + " disappeared"));
	}

}
