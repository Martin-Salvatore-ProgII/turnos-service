package com.example.turnos.availability.application.usecases;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import com.example.turnos.availability.application.exception.AvailabilityException;
import com.example.turnos.availability.domain.model.AgendaZone;
import com.example.turnos.availability.domain.model.Availability;
import com.example.turnos.availability.domain.model.AvailabilityHorizon;
import com.example.turnos.availability.domain.model.Occupancy;
import com.example.turnos.availability.domain.model.ScheduleSlots;
import com.example.turnos.availability.domain.model.Slot;
import com.example.turnos.availability.domain.ports.in.GetAvailabilityUseCase;
import com.example.turnos.availability.domain.ports.out.OccupancyPort;
import com.example.turnos.catalog.domain.model.ProfessionalAgenda;
import com.example.turnos.catalog.domain.ports.out.CatalogClientPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GetAvailabilityUseCaseImpl implements GetAvailabilityUseCase {

	// La agenda es de la feature catalog, que no tiene reglas propias: se usa su puerto de salida
	// (ADR-0062).
	private final CatalogClientPort catalogClientPort;
	private final OccupancyPort occupancyPort;
	private final AvailabilityHorizon availabilityHorizon;
	private final Clock clock;

	// El orden es el de CU-03: una fecha inválida no consulta a nadie, y un profesional inexistente o
	// deshabilitado no consulta a la cátedra. Si el catálogo o la cátedra fallan, la excepción del
	// adaptador sigue de largo: nunca se devuelve una disponibilidad incompleta (HU-06, criterio 7).
	@Override
	public Availability getAvailability(Long professionalId, LocalDate date) {
		ZonedDateTime now = ZonedDateTime.now(clock.withZone(AgendaZone.ZONE));
		checkDateInRange(date, now.toLocalDate());

		ProfessionalAgenda agenda = catalogClientPort.getProfessionalAgenda(professionalId)
				.orElseThrow(() -> new AvailabilityException(AvailabilityException.Code.PROFESSIONAL_NOT_FOUND,
						"Could not find professional with id: " + professionalId));
		if (!agenda.isEnabled()) {
			throw new AvailabilityException(AvailabilityException.Code.PROFESSIONAL_DISABLED,
					"Professional " + professionalId + " is disabled");
		}

		Set<LocalTime> occupied = occupiedStartTimes(occupancyPort.getOccupancies(professionalId, date), date);
		// Para hoy, solo los slots cuyo inicio todavía no pasó en Argentina (ADR-0015, ADR-0016).
		LocalTime notBefore = date.equals(now.toLocalDate()) ? now.toLocalTime() : LocalTime.MIN;

		// Un TreeMap por hora de inicio deja los slots ordenados aunque el día tenga varios horarios, y
		// si dos horarios se superpusieran, el mismo turno no aparece dos veces.
		TreeMap<LocalTime, Slot> free = new TreeMap<>();
		ScheduleSlots.ofDay(agenda, date).stream()
				.filter(slot -> !slot.getStartTime().isBefore(notBefore))
				.filter(slot -> !occupied.contains(slot.getStartTime()))
				.forEach(slot -> free.putIfAbsent(slot.getStartTime(), slot));

		return Availability.builder()
				.professionalId(agenda.getId())
				.professionalFirstName(agenda.getFirstName())
				.professionalLastName(agenda.getLastName())
				.date(date)
				.catalogVersion(agenda.getCatalogVersion())
				.slots(List.copyOf(free.values()))
				.build();
	}

	// Con el mismo código que la búsqueda del catálogo. "Hoy" es el de Argentina: a la 1 de la mañana
	// UTC del día 3 todavía es 2.
	private void checkDateInRange(LocalDate date, LocalDate today) {
		if (!availabilityHorizon.includes(date, today)) {
			throw new AvailabilityException(AvailabilityException.Code.DATE_OUT_OF_RANGE,
					"Date " + date + " must be between " + today + " and " + availabilityHorizon.lastDay(today));
		}
	}

	// Una ocupación bloquea el slot que empieza exactamente a su hora: así identifica la cátedra una
	// posición (REF §8, ADR-0067). CONFIRMED y HELD bloquean igual (ADR-0015). Las de otra fecha no
	// cuentan.
	private static Set<LocalTime> occupiedStartTimes(List<Occupancy> occupancies, LocalDate date) {
		return occupancies.stream()
				.filter(occupancy -> date.equals(occupancy.getDate()))
				.map(Occupancy::getStartTime)
				.collect(Collectors.toSet());
	}

}
