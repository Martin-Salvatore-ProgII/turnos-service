package com.example.turnos;

import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Lo propio del servicio de catálogo simulado ({@code docs/arq/pruebas.md}): la ruta de la agenda y
 * respuestas reales del catálogo, copiadas de {@code GET /api/professionals/{id}/agenda}. El
 * contenedor y la programación de respuestas son los de {@link FakeServer}.
 */
public final class FakeCatalog {

	/** Laura Medina (204), Pediatria, habilitada: lunes 11 a 15 y viernes 12 a 16, slots de 20 minutos. */
	public static final String AGENDA_204 = "catalog/agenda-204.json";

	/** Carolina Molina (206), deshabilitada: viene con {@code enabled: false} y sin horarios. */
	public static final String AGENDA_206 = "catalog/agenda-206.json";

	/** El 404 {@code PROFESSIONAL_NOT_FOUND} del catálogo, para el profesional 9999. */
	public static final String PROFESSIONAL_NOT_FOUND = "catalog/professional-not-found.json";

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private FakeCatalog() {
	}

	public static String agendaPath(long professionalId) {
		return "/api/professionals/" + professionalId + "/agenda";
	}

	/** Respuesta de la agenda de un profesional con el status y el cuerpo dados (tal cual, como texto). */
	public static ObjectNode agendaMapping(long professionalId, int status, String body) {
		ObjectNode mapping = JSON.createObjectNode();
		mapping.putObject("request").put("method", "GET").put("url", agendaPath(professionalId));
		ObjectNode response = mapping.putObject("response").put("status", status);
		response.putObject("headers").put("Content-Type", "application/json");
		response.put("body", body);
		return mapping;
	}

}
