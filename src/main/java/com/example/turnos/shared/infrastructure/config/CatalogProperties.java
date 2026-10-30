package com.example.turnos.shared.infrastructure.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Conexión con el servicio de catálogo. La URL viene de la configuración externa; los timeouts
 * tienen su valor inicial en {@code application.yaml} (ADR-0029, RNF-09).
 */
@ConfigurationProperties("turnos.catalog")
public record CatalogProperties(String apiUrl, Duration connectTimeout, Duration readTimeout) {
}
