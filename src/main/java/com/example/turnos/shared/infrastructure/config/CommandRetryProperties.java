package com.example.turnos.shared.infrastructure.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

// Reintentos de las operaciones con efectos que son seguras de repetir (ADR-0029, RNF-09): cuántos
// reintentos después del primer intento, y la espera antes del primero, que se duplica en cada uno.
@ConfigurationProperties("turnos.command-retry")
public record CommandRetryProperties(int retries, Duration initialBackoff) {
}
