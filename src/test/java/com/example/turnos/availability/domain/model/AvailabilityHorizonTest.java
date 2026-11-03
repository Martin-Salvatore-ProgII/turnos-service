package com.example.turnos.availability.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

// El rango de fechas que aceptan la disponibilidad y el inicio de una reserva (ADR-0015, ADR-0065).
class AvailabilityHorizonTest {

	private static final LocalDate TODAY = LocalDate.parse("2026-11-02");
	private final AvailabilityHorizon horizon = AvailabilityHorizon.builder().days(60).build();

	@Test
	void includesTodayAndTheLastDayOfTheHorizon() {
		assertThat(horizon.lastDay(TODAY)).isEqualTo(LocalDate.parse("2027-01-01"));
		assertThat(horizon.includes(TODAY, TODAY)).isTrue();
		assertThat(horizon.includes(LocalDate.parse("2027-01-01"), TODAY)).isTrue();
	}

	@Test
	void excludesYesterdayAndTheDayAfterTheHorizon() {
		assertThat(horizon.includes(TODAY.minusDays(1), TODAY)).isFalse();
		assertThat(horizon.includes(LocalDate.parse("2027-01-02"), TODAY)).isFalse();
	}

}
