package com.example.turnos;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;

/**
 * Le da al servicio una cátedra falsa que responde como la real: la integración en estado
 * PROVISIONED. Al arrancar, el servicio pide la integración y sin ella no arranca, así que toda
 * prueba que levanta la aplicación completa la importa. Ninguna prueba habla con la cátedra real
 * (ADR-0044).
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestCatedraConfiguration {

	@Bean
	public GenericContainer<?> catedraContainer() {
		return FakeCatedra.newContainerLikeTheCatedra();
	}

	// Los valores se leen recién cuando Spring los pide, con el contenedor ya levantado.
	@Bean
	public DynamicPropertyRegistrar catedraProperties(GenericContainer<?> catedraContainer) {
		return registry -> {
			registry.add("turnos.catedra.api-url", () -> FakeCatedra.baseUrl(catedraContainer));
			registry.add("turnos.catedra.technical-token", () -> FakeCatedra.TECHNICAL_TOKEN);
		};
	}

}
