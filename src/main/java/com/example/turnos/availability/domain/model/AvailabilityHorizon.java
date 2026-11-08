package com.example.turnos.availability.domain.model;

import java.time.LocalDate;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Cuántos días hacia adelante se puede consultar la disponibilidad (ADR-0015, ADR-0065). Es un valor
// configurable (RNF-09): lo arma la configuración y lo recibe el caso de uso, que así no depende de
// infrastructure. La regla la usan la disponibilidad y el inicio de una reserva.
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AvailabilityHorizon {
	private int days;

	// El último día que se puede pedir, contando desde hoy (el de Argentina, ADR-0016).
	public LocalDate lastDay(LocalDate today) {
		return today.plusDays(days);
	}

	// Entre hoy y el horizonte, ambos incluidos (ADR-0015, ADR-0065).
	public boolean includes(LocalDate date, LocalDate today) {
		return !date.isBefore(today) && !date.isAfter(lastDay(today));
	}
}
