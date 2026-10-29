package com.example.turnos;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.json.JsonMapper;

/**
 * Un servicio HTTP simulado con WireMock, en un contenedor: la cátedra o el catálogo (ADR-0044,
 * {@code docs/arq/pruebas.md}). Las respuestas se programan con la API de administración de WireMock,
 * por HTTP, así que no hace falta la librería de WireMock en el proyecto. Lo propio de cada servicio
 * está en {@link FakeCatedra} y {@link FakeCatalog}.
 */
public final class FakeServer {

	private static final DockerImageName IMAGE = DockerImageName.parse("wiremock/wiremock:3.13.2");
	private static final int PORT = 8080;
	static final String MAPPINGS_DIR = "/home/wiremock/mappings/";

	private static final HttpClient HTTP = HttpClient.newHttpClient();
	private static final JsonMapper JSON = JsonMapper.builder().build();

	private FakeServer() {
	}

	public static GenericContainer<?> newContainer() {
		return new GenericContainer<>(IMAGE)
				.withExposedPorts(PORT)
				.waitingFor(Wait.forHttp("/__admin/health").forStatusCode(200));
	}

	public static String baseUrl(GenericContainer<?> container) {
		return "http://" + container.getHost() + ":" + container.getMappedPort(PORT);
	}

	public static void stub(GenericContainer<?> container, String mappingJson) {
		admin(container, "POST", "/__admin/mappings", mappingJson);
	}

	public static void reset(GenericContainer<?> container) {
		admin(container, "POST", "/__admin/reset", "");
	}

	public static int countRequests(GenericContainer<?> container, String method, String path) {
		String body = admin(container, "POST", "/__admin/requests/count",
				"{\"method\":\"" + method + "\",\"url\":\"" + path + "\"}");
		return JSON.readTree(body).get("count").asInt();
	}

	public static String readResource(String name) {
		try (var in = FakeServer.class.getClassLoader().getResourceAsStream(name)) {
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static String admin(GenericContainer<?> container, String method, String path, String body) {
		HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl(container) + path))
				.method(method, HttpRequest.BodyPublishers.ofString(body))
				.header("Content-Type", "application/json")
				.build();
		try {
			HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() >= 300) {
				throw new IllegalStateException("WireMock respondió " + response.statusCode() + ": " + response.body());
			}
			return response.body();
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(e);
		}
	}

}
