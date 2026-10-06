package com.example.turnos.shared.infrastructure.config;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.security.KeyPair;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import com.example.turnos.TestJwtKeysConfiguration;
import com.example.turnos.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Prueba las reglas de acceso con la aplicación completa y la cadena de filtros real (RNF-04).
 * Los tokens se firman con el mismo emisor que usa el servicio; los inválidos se fabrican acá.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ TestcontainersConfiguration.class, TestJwtKeysConfiguration.class, SecurityConfigTest.TestController.class })
class SecurityConfigTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JwtEncoder jwtEncoder;

	// --- Rutas públicas ---

	@Test
	void registerIsPublic() throws Exception {
		// Un cuerpo inválido da 400: el pedido llegó al controller sin que la seguridad lo frenara.
		mockMvc.perform(post("/api/register").contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void authenticateIsPublic() throws Exception {
		mockMvc.perform(post("/api/authenticate").contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void healthIsPublic() throws Exception {
		mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
	}

	// --- Protegido por defecto ---

	@Test
	void protectedRouteWithoutTokenReturnsUnauthorized() throws Exception {
		mockMvc.perform(get("/api/test/protected"))
				.andExpect(status().isUnauthorized())
				.andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, startsWith("Bearer")));
	}

	@Test
	void unknownRouteWithoutTokenReturnsUnauthorized() throws Exception {
		mockMvc.perform(get("/api/does-not-exist")).andExpect(status().isUnauthorized());
	}

	@Test
	void protectedRouteWithValidTokenIdentifiesTheUserByLogin() throws Exception {
		mockMvc.perform(get("/api/test/protected").header(HttpHeaders.AUTHORIZATION, bearer(validToken("ROLE_USER"))))
				.andExpect(status().isOk())
				.andExpect(content().string("juan"));
	}

	@Test
	void expiredTokenReturnsUnauthorized() throws Exception {
		Instant twoHoursAgo = Instant.now().minus(Duration.ofHours(2));
		String expired = sign(jwtEncoder, twoHoursAgo, twoHoursAgo.plus(Duration.ofHours(1)), "ROLE_USER");

		mockMvc.perform(get("/api/test/protected").header(HttpHeaders.AUTHORIZATION, bearer(expired)))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void tokenSignedWithAnotherKeyReturnsUnauthorized() throws Exception {
		KeyPair anotherKeyPair = TestJwtKeysConfiguration.generateKeyPair();
		JwtEncoder anotherEncoder = new JwtConfig()
				.jwtEncoder((RSAPrivateKey) anotherKeyPair.getPrivate(), (RSAPublicKey) anotherKeyPair.getPublic());
		Instant now = Instant.now();
		String forged = sign(anotherEncoder, now, now.plus(Duration.ofHours(1)), "ROLE_ADMIN");

		mockMvc.perform(get("/api/test/protected").header(HttpHeaders.AUTHORIZATION, bearer(forged)))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void malformedTokenReturnsUnauthorized() throws Exception {
		mockMvc.perform(get("/api/test/protected").header(HttpHeaders.AUTHORIZATION, bearer("no-es-un-jwt")))
				.andExpect(status().isUnauthorized());
	}

	// --- Rutas administrativas ---

	@Test
	void adminRouteWithoutTokenReturnsUnauthorized() throws Exception {
		mockMvc.perform(get("/api/admin/test")).andExpect(status().isUnauthorized());
	}

	@Test
	void adminRouteWithCommonUserReturnsForbidden() throws Exception {
		mockMvc.perform(get("/api/admin/test").header(HttpHeaders.AUTHORIZATION, bearer(validToken("ROLE_USER"))))
				.andExpect(status().isForbidden());
	}

	@Test
	void adminRouteWithAdminIsAllowed() throws Exception {
		mockMvc.perform(get("/api/admin/test")
				.header(HttpHeaders.AUTHORIZATION, bearer(validToken("ROLE_USER", "ROLE_ADMIN"))))
				.andExpect(status().isOk());
	}

	// --- CORS cerrado ---

	@Test
	void requestFromAWebOriginIsRejected() throws Exception {
		mockMvc.perform(get("/actuator/health").header(HttpHeaders.ORIGIN, "https://otro-sitio.example"))
				.andExpect(status().isForbidden());
	}

	@Test
	void preflightFromAWebOriginIsRejected() throws Exception {
		mockMvc.perform(options("/api/authenticate")
				.header(HttpHeaders.ORIGIN, "https://otro-sitio.example")
				.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
				.andExpect(status().isForbidden());
	}

	private String validToken(String... authorities) {
		Instant now = Instant.now();
		return sign(jwtEncoder, now, now.plus(Duration.ofHours(1)), authorities);
	}

	private String sign(JwtEncoder encoder, Instant issuedAt, Instant expiresAt, String... authorities) {
		JwtClaimsSet claims = JwtClaimsSet.builder()
				.subject("juan")
				.claim(JwtConfig.AUTHORITIES_CLAIM, List.of(authorities))
				.issuedAt(issuedAt)
				.expiresAt(expiresAt)
				.build();
		JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).build();
		return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
	}

	private String bearer(String token) {
		return "Bearer " + token;
	}

	// Rutas solo para estas pruebas: el servicio todavía no tiene endpoints protegidos propios.
	@RestController
	static class TestController {

		@GetMapping("/api/test/protected")
		String protectedRoute(Authentication authentication) {
			return authentication.getName();
		}

		@GetMapping("/api/admin/test")
		String adminRoute() {
			return "admin";
		}

	}

}
