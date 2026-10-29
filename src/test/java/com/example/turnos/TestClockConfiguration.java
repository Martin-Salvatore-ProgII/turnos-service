package com.example.turnos;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Fija la hora de la aplicación completa, para las pruebas que dependen de "hoy" (docs/arq/pruebas.md:
 * pruebas deterministas, sin depender de la hora de la máquina). Es el lunes 2 de noviembre de 2026 a
 * las 12:30 de Argentina. Reemplaza al reloj real porque es {@code @Primary}. El JWT se sigue validando
 * con la hora del sistema.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestClockConfiguration {

	public static final Instant NOW = Instant.parse("2026-11-02T15:30:00Z");
	public static final LocalDate TODAY = LocalDate.parse("2026-11-02");

	@Bean
	@Primary
	public Clock fixedClock() {
		return Clock.fixed(NOW, ZoneOffset.UTC);
	}

}
