package com.example.turnos.shared.infrastructure.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

// Valores operativos de las reservas (RNF-09). Cuánto se conservan los eventId ya procesados
// (ADR-0008) y cuántas veces se reintenta procesar un evento que falla (ADR-0031). La reconciliación suma los suyos (ADR-0030).
@ConfigurationProperties("turnos.reservation")
public record ReservationProperties(Duration processedEventRetention, int eventRetries) {
}
