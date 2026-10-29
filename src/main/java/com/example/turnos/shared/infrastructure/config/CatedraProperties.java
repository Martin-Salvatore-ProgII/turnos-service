package com.example.turnos.shared.infrastructure.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Conexión con la API de la cátedra. La URL y el JWT técnico vienen del {@code .env} (ADR-0005,
 * ADR-0043); los timeouts tienen su valor inicial en {@code application.yaml} (ADR-0029).
 */
@ConfigurationProperties("turnos.catedra")
public record CatedraProperties(String apiUrl, String technicalToken, Duration connectTimeout, Duration readTimeout) {

	// Un record imprime todos sus campos en toString(). Se redefine para que el JWT técnico no
	// pueda terminar en un log por accidente.
	@Override
	public String toString() {
		return "CatedraProperties[connectTimeout=" + connectTimeout + ", readTimeout=" + readTimeout + "]";
	}

}
