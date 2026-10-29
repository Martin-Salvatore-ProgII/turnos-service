package com.example.turnos;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;

/**
 * Le da al servicio un catálogo falso, sin respuestas programadas: cada prueba programa las que
 * necesita con {@link FakeServer}. Toda prueba que levanta la aplicación completa la importa, porque
 * sin la URL del catálogo el contexto no arranca. Ninguna prueba habla con el catálogo real.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestCatalogConfiguration {

	@Bean
	public GenericContainer<?> catalogContainer() {
		return FakeServer.newContainer();
	}

	// Los valores se leen recién cuando Spring los pide, con el contenedor ya levantado.
	@Bean
	public DynamicPropertyRegistrar catalogProperties(GenericContainer<?> catalogContainer) {
		return registry -> registry.add("turnos.catalog.api-url", () -> FakeServer.baseUrl(catalogContainer));
	}

}
