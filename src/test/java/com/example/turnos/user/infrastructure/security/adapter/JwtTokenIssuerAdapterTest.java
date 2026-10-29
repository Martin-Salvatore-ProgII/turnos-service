package com.example.turnos.user.infrastructure.security.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.KeyPair;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.util.Set;

import com.example.turnos.TestJwtKeysConfiguration;
import com.example.turnos.shared.infrastructure.config.JwtConfig;
import com.example.turnos.shared.infrastructure.config.JwtProperties;
import com.example.turnos.user.domain.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

// Sin Spring: se arma el adaptador a mano con un par de claves generado para la prueba, y cada
// token se valida con la clave pública, que es lo que va a hacer el catálogo.
class JwtTokenIssuerAdapterTest {

	private final JwtProperties properties = new JwtProperties(null, Duration.ofHours(24), Duration.ofDays(30));

	private KeyPair keyPair;
	private JwtTokenIssuerAdapter tokenIssuer;

	@BeforeEach
	void setUp() throws Exception {
		keyPair = TestJwtKeysConfiguration.generateKeyPair();
		JwtConfig jwtConfig = new JwtConfig();
		RSAPrivateKey privateKey = (RSAPrivateKey) keyPair.getPrivate();
		tokenIssuer = new JwtTokenIssuerAdapter(jwtConfig.jwtEncoder(privateKey, jwtConfig.jwtPublicKey(privateKey)), properties);
	}

	@Test
	void tokenIdentifiesTheUserByLogin() {
		Jwt jwt = decode(tokenIssuer.issue(user(), false), keyPair);

		assertThat(jwt.getSubject()).isEqualTo("juan");
	}

	@Test
	void tokenCarriesTheAuthoritiesAsASortedList() {
		Jwt jwt = decode(tokenIssuer.issue(user(), false), keyPair);

		assertThat(jwt.getClaimAsStringList("auth")).containsExactly("ROLE_ADMIN", "ROLE_USER");
	}

	@Test
	void tokenDoesNotCarryPersonalOrInternalData() {
		Jwt jwt = decode(tokenIssuer.issue(user(), false), keyPair);

		assertThat(jwt.getClaims()).containsOnlyKeys("sub", "auth", "iat", "exp");
	}

	@Test
	void tokenIsSignedWithRs256() {
		Jwt jwt = decode(tokenIssuer.issue(user(), false), keyPair);

		assertThat(jwt.getHeaders()).containsEntry("alg", "RS256");
	}

	@Test
	void tokenLasts24HoursByDefault() {
		Jwt jwt = decode(tokenIssuer.issue(user(), false), keyPair);

		assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt())).isEqualTo(Duration.ofHours(24));
	}

	@Test
	void tokenLasts30DaysWithRememberMe() {
		Jwt jwt = decode(tokenIssuer.issue(user(), true), keyPair);

		assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt())).isEqualTo(Duration.ofDays(30));
	}

	@Test
	void tokenIsRejectedWhenValidatedWithAnotherPublicKey() throws Exception {
		String token = tokenIssuer.issue(user(), false);
		KeyPair anotherKeyPair = TestJwtKeysConfiguration.generateKeyPair();

		assertThatThrownBy(() -> decode(token, anotherKeyPair)).isInstanceOf(JwtException.class);
	}

	private Jwt decode(String token, KeyPair keys) {
		return NimbusJwtDecoder.withPublicKey((RSAPublicKey) keys.getPublic()).build().decode(token);
	}

	private User user() {
		return User.builder()
				.id(1L)
				.login("juan")
				.email("juan@example.com")
				.passwordHash("hashed")
				.authorities(Set.of("ROLE_USER", "ROLE_ADMIN"))
				.build();
	}

}
