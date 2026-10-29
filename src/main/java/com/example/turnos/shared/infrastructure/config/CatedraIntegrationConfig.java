package com.example.turnos.shared.infrastructure.config;

import com.example.turnos.shared.infrastructure.rest.CatedraReadExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Obtiene al arrancar la configuración de la integración con la cátedra (ADR-0005, ENUNCIADO §5
 * punto 3). Si no se puede obtener o no está {@code PROVISIONED}, el servicio no arranca.
 *
 * <p>Está en {@code shared} y no en una feature porque solo la consume infraestructura (la
 * configuración de Kafka, en la M5); ningún caso de uso la necesita.
 */
@Slf4j
@Configuration
public class CatedraIntegrationConfig {

	static final String INTEGRATION_PATH = "/api/student/integration";

	private static final String PROVISIONED = "PROVISIONED";

	// Se obtiene al crear el bean, antes de que Spring arme lo que depende de estos datos. Si
	// falla, Spring corta el arranque con el mensaje de la excepción. El reintento y los mensajes
	// de falla son los de toda lectura a la cátedra (CatedraReadExecutor).
	@Bean
	public CatedraIntegration catedraIntegration(RestClient catedraRestClient, CatedraReadExecutor catedraReadExecutor) {
		CatedraIntegration integration = catedraReadExecutor.read(() -> catedraRestClient.get()
				.uri(INTEGRATION_PATH)
				.retrieve()
				.body(CatedraIntegration.class));
		if (!PROVISIONED.equals(integration.provisioningStatus())) {
			throw new CatedraIntegrationException("Catedra integration is in state " + integration.provisioningStatus());
		}
		log.info("Catedra integration obtained, in state {}", PROVISIONED);
		return integration;
	}

}
