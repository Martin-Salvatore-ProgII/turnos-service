package com.example.turnos.shared.infrastructure.rest;

import java.time.Duration;

import javax.net.ssl.SSLContext;

import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.DefaultClientTlsStrategy;
import org.apache.hc.core5.util.Timeout;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;

/**
 * Fábrica de la conexión HTTP de todo cliente REST de este servicio (hacia la cátedra y hacia el
 * catálogo), con los timeouts configurados y sin reintentos propios.
 */
public final class NoRetryRequestFactory {

	private NoRetryRequestFactory() {
	}

	// Los únicos reintentos son los del ADR-0029, que dejan rastro en el log. El cliente de
	// java.net.http repite por su cuenta un GET cuyo servidor corta la conexión (con HTTP/1.1, que es
	// lo que habla la cátedra real) y no hay forma de apagarlo en un solo cliente: la propiedad del
	// JDK es global a la JVM. El de Apache sí: disableAutomaticRetries().
	public static ClientHttpRequestFactory create(Duration connectTimeout, Duration readTimeout) {
		return create(connectTimeout, readTimeout, null);
	}

	// Con sslContext, las conexiones HTTPS confían solo en lo que él contenga (el certificado autofirmado
	// del catálogo, ADR-0035); sin él, en los certificados de la JVM. DefaultClientTlsStrategy verifica
	// además que el certificado sea del host al que se llama: confiar en el certificado no alcanza.
	public static ClientHttpRequestFactory create(Duration connectTimeout, Duration readTimeout, SSLContext sslContext) {
		ConnectionConfig connectionConfig = ConnectionConfig.custom()
				.setConnectTimeout(Timeout.of(connectTimeout))
				.setSocketTimeout(Timeout.of(readTimeout))
				.build();
		PoolingHttpClientConnectionManagerBuilder connectionManagerBuilder = PoolingHttpClientConnectionManagerBuilder.create()
				.setDefaultConnectionConfig(connectionConfig);
		if (sslContext != null) {
			connectionManagerBuilder.setTlsSocketStrategy(new DefaultClientTlsStrategy(sslContext));
		}
		PoolingHttpClientConnectionManager connectionManager = connectionManagerBuilder.build();
		CloseableHttpClient httpClient = HttpClients.custom()
				.setConnectionManager(connectionManager)
				.disableAutomaticRetries()
				.build();
		return new HttpComponentsClientHttpRequestFactory(httpClient);
	}

}
