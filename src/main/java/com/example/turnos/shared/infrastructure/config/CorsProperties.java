package com.example.turnos.shared.infrastructure.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("turnos.security.cors")
public record CorsProperties(List<String> allowedOrigins) {
}
