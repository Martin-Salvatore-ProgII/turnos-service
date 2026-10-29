package com.example.turnos.shared.infrastructure.config;

import java.net.http.HttpClient;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.client.RestClient;

/**
 * Cliente HTTP hacia el servicio de catálogo. Lo usa el adaptador de la feature {@code catalog}.
 */
@Configuration
@EnableConfigurationProperties(CatalogProperties.class)
public class CatalogRestClientConfig {

	// Mismos timeouts y mismo HttpClient que hacia la cátedra, que no reintenta por su cuenta
	// (ADR-0029).
	@Bean
	public RestClient catalogRestClient(CatalogProperties properties) {
		HttpClient httpClient = HttpClient.newBuilder()
				.connectTimeout(properties.connectTimeout())
				.build();
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
		requestFactory.setReadTimeout(properties.readTimeout());
		return RestClient.builder()
				.baseUrl(properties.apiUrl())
				.requestFactory(requestFactory)
				.requestInterceptor(forwardUserToken())
				.build();
	}

	// Todo pedido al catálogo lleva el mismo JWT de usuario que llegó a turnos (ADR-0034). Se toma
	// del pedido en curso, ya validado por Spring Security, y no viaja por las firmas del dominio:
	// cómo se autentica turnos ante el catálogo es un detalle de transporte.
	static ClientHttpRequestInterceptor forwardUserToken() {
		return (request, body, execution) -> {
			Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
			if (!(authentication instanceof JwtAuthenticationToken jwt)) {
				// Toda llamada al catálogo nace de un pedido del usuario (ADR-0034): sin token es un error
				// de programación, no un caso a manejar.
				throw new IllegalStateException("No user token to forward to the catalog");
			}
			request.getHeaders().setBearerAuth(jwt.getToken().getTokenValue());
			return execution.execute(request, body);
		};
	}

}
