package com.example.turnos.shared.infrastructure.config;

import java.net.http.HttpClient;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Cliente HTTP hacia la API de la cátedra, compartido por todo lo que la llama: el pedido de la
 * integración al arrancar y, más adelante, las ocupaciones, los holds y las reservas.
 */
@Configuration
@EnableConfigurationProperties(CatedraProperties.class)
public class CatedraRestClientConfig {

	// Todo pedido a la cátedra va con el JWT técnico (REF §5.3). Sin timeouts, una cátedra que
	// acepta la conexión y no responde dejaría el pedido esperando para siempre.
	// Se usa el HttpClient de java.net.http y no HttpURLConnection, porque este último repite un GET
	// por su cuenta cuando se corta la conexión: un reintento oculto que se sumaría a los nuestros
	// y no quedaría en el log (ADR-0029).
	@Bean
	public RestClient catedraRestClient(CatedraProperties properties) {
		HttpClient httpClient = HttpClient.newBuilder()
				.connectTimeout(properties.connectTimeout())
				.build();
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
		requestFactory.setReadTimeout(properties.readTimeout());
		return RestClient.builder()
				.baseUrl(properties.apiUrl())
				.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.technicalToken())
				.requestFactory(requestFactory)
				.build();
	}

}
