package com.example.turnos.shared.infrastructure.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// La hora se inyecta, para que las pruebas la puedan fijar (docs/arq/pruebas.md). Los casos de uso
// la pasan a la zona de la agenda cuando necesitan "hoy" o "ahora" (ADR-0016).
@Configuration
public class ClockConfig {

	@Bean
	public Clock clock() {
		return Clock.systemUTC();
	}

}
