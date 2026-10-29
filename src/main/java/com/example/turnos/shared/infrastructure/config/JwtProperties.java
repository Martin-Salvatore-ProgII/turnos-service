package com.example.turnos.shared.infrastructure.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;

@ConfigurationProperties("turnos.security.jwt")
public record JwtProperties(Resource privateKeyLocation, Duration validity, Duration rememberMeValidity) {
}
