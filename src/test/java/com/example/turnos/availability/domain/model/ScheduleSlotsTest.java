package com.example.turnos.availability.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

import com.example.turnos.catalog.domain.model.WeeklySchedule;
import org.junit.jupiter.api.Test;

// La regla que corta un horario en slots (ADR-0015). Los esperados se calcularon aparte.
class ScheduleSlotsTest {

	@Test
	void offersOnlySlotsThatEndInsideTheSchedule() {
		// El ejemplo de HU-06: de 09:00 a 12:00 con 50 minutos, el de 11:30 terminaría a las 12:20.
		List<Slot> slots = ScheduleSlots.of(schedule("09:00", "12:00", 50));

		assertThat(starts(slots)).containsExactly("09:00", "09:50", "10:40");
		assertThat(slots.get(2).getEndTime()).isEqualTo(LocalTime.parse("11:30"));
	}

	@Test
	void cutsTheRealMondayScheduleOfLauraMedina() {
		// Horario 306 del catálogo real: lunes de 11:00 a 15:00, slots de 20 minutos.
		List<Slot> slots = ScheduleSlots.of(schedule("11:00", "15:00", 20));

		assertThat(slots).hasSize(12);
		assertThat(starts(slots)).startsWith("11:00", "11:20").endsWith("14:40");
		assertThat(slots.get(11).getEndTime()).isEqualTo(LocalTime.parse("15:00"));
	}

	@Test
	void givesNothingWhenTheSlotDoesNotFit() {
		assertThat(ScheduleSlots.of(schedule("09:00", "09:20", 30))).isEmpty();
	}

	@Test
	void givesNothingForInvalidData() {
		// No se suponen datos válidos (RNF-09): sin duración, o con el fin antes del inicio.
		assertThat(ScheduleSlots.of(schedule("09:00", "12:00", 0))).isEmpty();
		assertThat(ScheduleSlots.of(schedule("09:00", "12:00", -30))).isEmpty();
		assertThat(ScheduleSlots.of(schedule("12:00", "09:00", 30))).isEmpty();
		assertThat(ScheduleSlots.of(null)).isEmpty();
	}

	@Test
	void doesNotWrapAroundMidnight() {
		// Con LocalTime.plusMinutes, 23:30 + 30 da 00:00 y el corte no terminaría nunca.
		assertThat(starts(ScheduleSlots.of(schedule("23:00", "23:59", 30)))).containsExactly("23:00");
	}

	private static WeeklySchedule schedule(String start, String end, int duration) {
		return WeeklySchedule.builder()
				.id(1L)
				.dayOfWeek(DayOfWeek.MONDAY)
				.startTime(LocalTime.parse(start))
				.endTime(LocalTime.parse(end))
				.slotDurationMinutes(duration)
				.build();
	}

	private static List<String> starts(List<Slot> slots) {
		return slots.stream().map(slot -> slot.getStartTime().toString()).toList();
	}

}
