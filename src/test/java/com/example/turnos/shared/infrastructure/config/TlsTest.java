package com.example.turnos.shared.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import javax.net.ssl.SSLHandshakeException;

import com.example.turnos.TestCatalogConfiguration;
import com.example.turnos.TestCatedraConfiguration;
import com.example.turnos.TestJwtKeysConfiguration;
import com.example.turnos.TestcontainersConfiguration;
import com.example.turnos.shared.infrastructure.rest.NoRetryRequestFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatusCode;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * HTTPS (ADR-0035): el servicio levantado de verdad, en un puerto real, con el perfil tls y el certificado
 * de prueba de src/test/resources/tls (válido solo para localhost). Prueba las dos puntas: el servidor
 * habla solo TLS, y el cliente que turnos usa para llamar al catálogo confía en el certificado autofirmado
 * y verifica el nombre del host. Las demás pruebas corren sin el perfil, en HTTP.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
		"TLS_CERTIFICATE_LOCATION=classpath:tls/test.crt",
		"TLS_PRIVATE_KEY_LOCATION=classpath:tls/test.key" })
@ActiveProfiles("tls")
@Import({ TestcontainersConfiguration.class, TestJwtKeysConfiguration.class, TestCatedraConfiguration.class,
		TestCatalogConfiguration.class })
class TlsTest {

	private static final Duration TIMEOUT = Duration.ofSeconds(5);

	@LocalServerPort
	private int port;

	@Autowired
	private SslBundles sslBundles;

	@Autowired
	private CatalogProperties catalogProperties;

	@AfterEach
	void clearSecurityContext() {
		SecurityContextHolder.clearContext();
	}

	// El cliente del catálogo, armado como en la aplicación (con el bundle "catalog" del perfil tls), llega
	// por HTTPS. El 401 es la respuesta de la API a un token que no firmó este servicio: la conexión cifrada
	// funcionó y el pedido llegó a la seguridad.
	@Test
	void theCatalogClientTrustsTheSelfSignedCertificate() {
		assertThat(catalogProperties.sslBundle()).isEqualTo("catalog");
		RestClient client = new CatalogRestClientConfig().catalogRestClient(
				new CatalogProperties("https://localhost:" + port, TIMEOUT, TIMEOUT, catalogProperties.sslBundle()),
				sslBundles);
		forwardSomeUserToken();

		HttpStatusCode status = client.get().uri("/api/reservations")
				.exchange((request, response) -> response.getStatusCode());

		assertThat(status.value()).isEqualTo(401);
	}

	// Sin el certificado entre los de confianza, el handshake falla: un cliente cualquiera no lo acepta.
	@Test
	void aClientThatDoesNotTrustTheCertificateIsRejected() {
		RestClient client = RestClient.builder()
				.baseUrl("https://localhost:" + port)
				.requestFactory(NoRetryRequestFactory.create(TIMEOUT, TIMEOUT))
				.build();

		assertThatThrownBy(() -> client.get().uri("/api/reservations").retrieve().toBodilessEntity())
				.isInstanceOf(ResourceAccessException.class)
				.cause().isInstanceOf(SSLHandshakeException.class)
				// La cadena del certificado no llega a ningún certificado de confianza.
				.hasMessageContaining("PKIX path building failed");
	}

	// Confiar en el certificado no alcanza: tiene que ser del host al que se llama. El de prueba es de
	// localhost, así que por 127.0.0.1 se rechaza aunque sea la misma máquina.
	@Test
	void theHostNameOfTheCertificateIsVerified() {
		RestClient client = RestClient.builder()
				.baseUrl("https://127.0.0.1:" + port)
				.requestFactory(NoRetryRequestFactory.create(TIMEOUT, TIMEOUT,
						sslBundles.getBundle("catalog").createSslContext()))
				.build();

		assertThatThrownBy(() -> client.get().uri("/api/reservations").retrieve().toBodilessEntity())
				.isInstanceOf(ResourceAccessException.class)
				.cause().isInstanceOf(SSLHandshakeException.class)
				// El certificado es de confianza, pero no nombra a ese host.
				.hasMessageContaining("No subject alternative names matching IP address 127.0.0.1");
	}

	// Sin puerto HTTP: un pedido en texto plano no llega a la API.
	@Test
	void plainHttpIsNotServed() {
		RestClient client = RestClient.builder()
				.baseUrl("http://localhost:" + port)
				.requestFactory(NoRetryRequestFactory.create(TIMEOUT, TIMEOUT))
				.build();

		HttpStatusCode status = client.get().uri("/api/reservations")
				.exchange((request, response) -> response.getStatusCode());

		assertThat(status.value()).isEqualTo(400);
	}

	private static void forwardSomeUserToken() {
		Jwt jwt = Jwt.withTokenValue("not-signed-by-turnos").header("alg", "none").claim("sub", "juan").build();
		SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
	}

}
