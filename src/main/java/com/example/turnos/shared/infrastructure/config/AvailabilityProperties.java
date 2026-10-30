package com.example.turnos.shared.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

// Horizonte de fechas de la disponibilidad (ADR-0015, ADR-0065, RNF-09). Tiene que valer lo mismo que
// el del catálogo.
@ConfigurationProperties("turnos.availability")
public record AvailabilityProperties(int horizonDays) {
}
