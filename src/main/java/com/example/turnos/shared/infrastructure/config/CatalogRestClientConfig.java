package com.example.turnos.shared.infrastructure.config;

import com.example.turnos.shared.infrastructure.rest.NoRetryRequestFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestInterceptor;
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

	// Misma conexión que hacia la cátedra: con timeouts y sin reintentos propios (ADR-0029).
	@Bean
	public RestClient catalogRestClient(CatalogProperties properties) {
		return RestClient.builder()
				.baseUrl(properties.apiUrl())
				.requestFactory(NoRetryRequestFactory.create(properties.connectTimeout(), properties.readTimeout()))
				.requestInterceptor(forwardUserToken())
				.build();
	}

	// Todo pedido al catálogo lleva el mismo JWT de usuario que llegó a turnos (ADR-0034). Se toma
	// del pedido en curso, ya validado por Spring Security, y no viaja por las firmas del dominio
	// (ADR-0066): por eso el catálogo solo se puede llamar dentro de un pedido del usuario.
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
