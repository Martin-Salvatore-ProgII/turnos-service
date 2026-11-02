package com.example.turnos.shared.infrastructure.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

// Activa @Scheduled. Hoy lo usa la reconciliación de procesos de reserva (ADR-0030).
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
