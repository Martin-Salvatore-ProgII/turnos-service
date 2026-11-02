package com.example.turnos.shared.infrastructure.config;

import com.example.turnos.shared.infrastructure.rest.NoRetryRequestFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
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
	// La conexión no reintenta por su cuenta: un reintento oculto se sumaría a los nuestros y no
	// quedaría en el log (ADR-0029). Ni HttpURLConnection ni el cliente de java.net.http cumplen eso.
	@Bean
	public RestClient catedraRestClient(CatedraProperties properties) {
		return RestClient.builder()
				.baseUrl(properties.apiUrl())
				.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.technicalToken())
				.requestFactory(NoRetryRequestFactory.create(properties.connectTimeout(), properties.readTimeout()))
				.build();
	}

}
