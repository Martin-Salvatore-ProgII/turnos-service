package com.example.turnos.reservation.application.usecases;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import org.springframework.stereotype.Component;

import com.example.turnos.reservation.application.exception.ReservationException;
import com.example.turnos.reservation.application.exception.ReservationProcessConflictException;
import com.example.turnos.reservation.domain.model.CatedraAppointment;
import com.example.turnos.reservation.domain.model.CatedraAppointmentStatus;
import com.example.turnos.reservation.domain.model.ReservationProcess;
import com.example.turnos.reservation.domain.model.ReservationStatus;
import com.example.turnos.reservation.domain.model.ReservationTrigger;
import com.example.turnos.reservation.domain.ports.in.ReconcileReservationProcessesUseCase;
import com.example.turnos.reservation.domain.ports.out.AppointmentPort;
import com.example.turnos.reservation.domain.ports.out.ReservationProcessRepository;
import com.example.turnos.availability.domain.model.AgendaZone;
import com.example.turnos.user.domain.model.User;
import com.example.turnos.user.domain.ports.out.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReconcileReservationProcessesUseCaseImpl implements ReconcileReservationProcessesUseCase {

	// Motivos con los que se cierra un proceso desde acá. Son códigos estables que la app traduce a su
	// propio mensaje (contratos.md). Los dos primeros son los de la cátedra.
	static final String HOLD_EXPIRED = "HOLD_EXPIRED";
	static final String CATEDRA_REPORTED_FAILED = "CATEDRA_REPORTED_FAILED";
	static final String HOLD_NOT_REGISTERED = "INTEGRATION_UNAVAILABLE";
	static final String CANCELLED_BY_CATEDRA = "CATEDRA";

	private static final EnumSet<ReservationStatus> WAITING_WITHOUT_PHONE = EnumSet.of(ReservationStatus.HELD,
			ReservationStatus.AWAITING_REQUEST, ReservationStatus.AWAITING_PHONE);

	// El dueño y la cátedra no tienen reglas propias sobre estas lecturas: sus puertos de salida (ADR-0062).
	private final ReservationProcessRepository reservationProcessRepository;
	private final UserRepository userRepository;
	private final AppointmentPort appointmentPort;
	private final Clock clock;

	// Cada ciclo es un intento y el siguiente es el reintento: sin bucles ni esperas (ADR-0030). Cada proceso
	// se resuelve por separado y sin transacción común, así que uno que falla no frena a los demás.
	@Override
	public int reconcileReservationProcesses(Duration margin) {
		Instant now = clock.instant();
		Instant cutoff = now.minus(margin);
		int changed = 0;

		// Sin teléfono enviado la cátedra no pudo confirmar: se decide sin consultarla (ADR-0030).
		for (ReservationProcess process : reservationProcessRepository.findStartedCreatedBefore(cutoff)) {
			changed += closeLocally(process, ReservationTrigger.HOLD_FAILED, HOLD_NOT_REGISTERED);
		}
		for (ReservationProcess process : reservationProcessRepository
				.findByStatusInAndExpiresAtBefore(WAITING_WITHOUT_PHONE, cutoff)) {
			changed += closeLocally(process, ReservationTrigger.EXPIRED_WITHOUT_PHONE, HOLD_EXPIRED);
		}

		// Con el teléfono enviado, o ya confirmada, solo la cátedra sabe cómo terminó.
		List<ReservationProcess> submitted = reservationProcessRepository
				.findByStatusInAndExpiresAtBefore(EnumSet.of(ReservationStatus.PHONE_SUBMITTED), cutoff);
		LocalDate today = now.atZone(AgendaZone.ZONE).toLocalDate();
		List<ReservationProcess> confirmed = reservationProcessRepository.findConfirmedFrom(today);
		changed += askTheCatedra(submitted, this::resolveSubmitted);
		changed += askTheCatedra(confirmed, this::resolveConfirmed);
		return changed;
	}

	private int closeLocally(ReservationProcess process, ReservationTrigger trigger, String reason) {
		if (!process.apply(trigger)) {
			return 0;
		}
		process.setFailureReason(reason);
		return save(process, trigger);
	}

	// Una consulta por dueño y no por proceso, y solo cuando hay una duda real (ADR-0030). Si la cátedra no
	// responde, los procesos de ese dueño quedan como están.
	private int askTheCatedra(List<ReservationProcess> processes, Resolver resolver) {
		Map<Long, List<ReservationProcess>> byOwner = new LinkedHashMap<>();
		processes.forEach(process -> byOwner.computeIfAbsent(process.getUserId(), id -> new java.util.ArrayList<>())
				.add(process));
		int changed = 0;
		for (Map.Entry<Long, List<ReservationProcess>> owner : byOwner.entrySet()) {
			Optional<User> user = userRepository.findById(owner.getKey());
			if (user.isEmpty()) {
				log.warn("Reservation processes of user {} skipped: the user does not exist", owner.getKey());
				continue;
			}
			List<CatedraAppointment> appointments;
			try {
				appointments = appointmentPort.findAppointments(user.get().getExternalPatientId());
			} catch (ReservationException e) {
				log.warn("Reconciliation of {} process(es) postponed: {}", owner.getValue().size(), e.getMessage());
				continue;
			}
			for (ReservationProcess process : owner.getValue()) {
				Optional<CatedraAppointment> appointment = appointments.stream()
						.filter(candidate -> process.getReservationProcessId() != null
								&& process.getReservationProcessId().equals(candidate.getReservationProcessId()))
						.findFirst();
				// Ausente es lo mismo que PHONE_PENDING: se revisa en el próximo ciclo (CU-05, 4a).
				changed += appointment.map(found -> resolver.resolve(process, found)).orElse(0);
			}
		}
		return changed;
	}

	private int resolveSubmitted(ReservationProcess process, CatedraAppointment appointment) {
		if (appointment.getStatus() == CatedraAppointmentStatus.CONFIRMED) {
			return change(process, ReservationTrigger.APPOINTMENT_CONFIRMED, p -> {
				p.setReservationId(appointment.getReservationId());
				p.setConfirmedAt(appointment.getConfirmedAt() != null ? appointment.getConfirmedAt() : clock.instant());
			});
		}
		if (appointment.getStatus() == CatedraAppointmentStatus.FAILED) {
			return change(process, ReservationTrigger.CATEDRA_REPORTED_FAILED, p -> p.setFailureReason(CATEDRA_REPORTED_FAILED));
		}
		return 0;
	}

	private int resolveConfirmed(ReservationProcess process, CatedraAppointment appointment) {
		if (appointment.getStatus() != CatedraAppointmentStatus.CANCELLED) {
			return 0;
		}
		return change(process, ReservationTrigger.APPOINTMENT_CANCELLED, p -> {
			p.setCancelledAt(appointment.getCancelledAt() != null ? appointment.getCancelledAt() : clock.instant());
			p.setCancellationReason(appointment.getCancellationReason());
			p.setCancelledBy(CANCELLED_BY_CATEDRA);
		});
	}

	private int change(ReservationProcess process, ReservationTrigger trigger, Consumer<ReservationProcess> data) {
		if (!process.apply(trigger)) {
			return 0;
		}
		data.accept(process);
		return save(process, trigger);
	}

	// Si el proceso cambió mientras se resolvía (llegó un evento), el evento ganó: se deja para el próximo ciclo.
	private int save(ReservationProcess process, ReservationTrigger trigger) {
		try {
			reservationProcessRepository.update(process.getId(), process);
			log.info("Reservation process {} reconciled: {} -> {}", process.getId(), trigger, process.getStatus());
			return 1;
		} catch (ReservationProcessConflictException e) {
			log.info("Reservation process {} changed while reconciling, left for the next cycle", process.getId());
			return 0;
		}
	}

	@FunctionalInterface
	private interface Resolver {
		int resolve(ReservationProcess process, CatedraAppointment appointment);
	}

}
