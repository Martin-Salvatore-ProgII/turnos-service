package com.example.turnos.user.infrastructure.security.adapter;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import com.example.turnos.shared.infrastructure.config.JwtProperties;
import com.example.turnos.user.domain.model.User;
import com.example.turnos.user.domain.ports.out.TokenIssuerPort;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JwtTokenIssuerAdapter implements TokenIssuerPort {

	// Nombre del claim con los roles. Es parte del contrato con el catálogo, que lo lee al validar.
	static final String AUTHORITIES_CLAIM = "auth";

	private final JwtEncoder jwtEncoder;
	private final JwtProperties jwtProperties;

	@Override
	public String issue(User user, boolean rememberMe) {
		Instant now = Instant.now();
		// Las dos vigencias son valores operativos configurables (ADR-0033), por eso viven acá
		// y no en el caso de uso, que solo indica si el usuario pidió una sesión larga.
		Duration validity = rememberMe ? jwtProperties.rememberMeValidity() : jwtProperties.validity();

		// El token lleva lo mínimo para identificar y autorizar: quién es (sub) y qué roles tiene.
		// El UUID del usuario no viaja: este servicio lo busca en su base cuando lo necesita.
		JwtClaimsSet claims = JwtClaimsSet.builder()
				.subject(user.getLogin())
				.claim(AUTHORITIES_CLAIM, user.getAuthorities().stream().sorted().toList())
				.issuedAt(now)
				.expiresAt(now.plus(validity))
				.build();
		JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).build();

		return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
	}

}
