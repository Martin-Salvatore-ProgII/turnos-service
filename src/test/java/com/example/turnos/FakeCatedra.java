package com.example.turnos;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.MountableFile;

/**
 * Lo propio de la cátedra simulada (ADR-0044): el JWT técnico de las pruebas y la respuesta de la
 * integración. El contenedor y la programación de respuestas son los de {@link FakeServer}.
 */
public final class FakeCatedra {

	public static final String TECHNICAL_TOKEN = "test-technical-token";

	/** Respuesta de {@code GET /api/student/integration} en estado PROVISIONED, con el formato de la REF §5.3. */
	public static final String INTEGRATION_MAPPING = "catedra/integration.json";

	private FakeCatedra() {
	}

	/** Contenedor que ya arranca respondiendo como la cátedra real: la integración en estado PROVISIONED. */
	public static GenericContainer<?> newContainerLikeTheCatedra() {
		return FakeServer.newContainer()
				.withCopyFileToContainer(MountableFile.forClasspathResource(INTEGRATION_MAPPING),
						FakeServer.MAPPINGS_DIR + "integration.json");
	}

}
