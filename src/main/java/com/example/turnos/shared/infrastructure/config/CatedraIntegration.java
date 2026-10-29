package com.example.turnos.shared.infrastructure.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Configuración de la integración que asigna la cátedra (REF §5.3). Solo lleva los campos que usa
 * turnos: Kafka y sus dos topics de reservas. Redis y el topic del catálogo se ignoran: turnos no
 * los usa (los holds van solo por REST; REF §14.1 no da acceso a
 * {@code catedra:holds:*}).
 *
 * <p>Vive en memoria: no se guarda en la base ni en archivos (ADR-0005).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CatedraIntegration(
		String groupId,
		String kafkaBootstrapServers,
		String kafkaConsumerGroupId,
		String kafkaAppointmentActionsTopic,
		String kafkaAppointmentPhoneTopic,
		String provisioningStatus) {

	// Ningún valor de la integración puede aparecer en un log (RNF-02). Un record los imprimiría
	// todos en toString(); solo se deja ver el estado.
	@Override
	public String toString() {
		return "CatedraIntegration[provisioningStatus=" + provisioningStatus + "]";
	}

}
