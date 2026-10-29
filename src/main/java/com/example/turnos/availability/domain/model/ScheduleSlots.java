package com.example.turnos.availability.domain.model;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import com.example.turnos.catalog.domain.model.WeeklySchedule;

/**
 * La regla que corta un horario semanal en slots: desde el inicio, de a la duración del slot, y solo
 * los que terminan dentro del horario (ADR-0015). Está en el dominio porque la usan la
 * disponibilidad y, en la reserva, la validación de que el turno pedido exista (ADR-0062).
 */
public final class ScheduleSlots {

	private ScheduleSlots() {
	}

	// Se cuenta en minutos del día y no con LocalTime.plusMinutes: pasada la medianoche, LocalTime
	// vuelve a las 00:00 y un horario que termina tarde nunca cortaría. Un horario sin duración o con
	// el fin antes del inicio no da slots (RNF-09: no se suponen datos válidos).
	public static List<Slot> of(WeeklySchedule schedule) {
		List<Slot> slots = new ArrayList<>();
		if (schedule == null || schedule.getStartTime() == null || schedule.getEndTime() == null
				|| schedule.getSlotDurationMinutes() <= 0) {
			return slots;
		}
		int duration = schedule.getSlotDurationMinutes();
		int end = minutesOfDay(schedule.getEndTime());
		for (int start = minutesOfDay(schedule.getStartTime()); start + duration <= end; start += duration) {
			slots.add(Slot.builder()
					.startTime(LocalTime.of(start / 60, start % 60))
					.endTime(LocalTime.of((start + duration) / 60, (start + duration) % 60))
					.build());
		}
		return slots;
	}

	private static int minutesOfDay(LocalTime time) {
		return time.getHour() * 60 + time.getMinute();
	}

}
