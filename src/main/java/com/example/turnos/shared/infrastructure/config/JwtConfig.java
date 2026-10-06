package com.example.turnos.shared.infrastructure.config;

import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPublicKeySpec;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * Claves del JWT de usuario. Este servicio firma con la clave privada RSA; el catálogo valida con
 * la pública y no puede emitir tokens (ADR-0032).
 */
@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class JwtConfig {

	// Nombre del claim con los roles. Es parte del contrato con el catálogo, que lo lee al validar.
	public static final String AUTHORITIES_CLAIM = "auth";

	// La clave privada llega como archivo externo, fuera de Git (ADR-0043).
	@Bean
	public RSAPrivateKey jwtPrivateKey(JwtProperties properties) throws IOException {
		try (InputStream pem = properties.privateKeyLocation().getInputStream()) {
			return RsaKeyConverters.pkcs8().convert(pem);
		}
	}

	// La clave pública no se configura aparte: se calcula a partir de la privada. Así el
	// servicio necesita un solo archivo y las dos mitades del par no pueden quedar desparejas.
	@Bean
	public RSAPublicKey jwtPublicKey(RSAPrivateKey jwtPrivateKey) throws GeneralSecurityException {
		RSAPrivateCrtKey privateKey = (RSAPrivateCrtKey) jwtPrivateKey;
		RSAPublicKeySpec publicKeySpec = new RSAPublicKeySpec(privateKey.getModulus(), privateKey.getPublicExponent());
		return (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(publicKeySpec);
	}

	@Bean
	public JwtEncoder jwtEncoder(RSAPrivateKey jwtPrivateKey, RSAPublicKey jwtPublicKey) {
		RSAKey rsaKey = new RSAKey.Builder(jwtPublicKey).privateKey(jwtPrivateKey).build();
		return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(rsaKey)));
	}

	// Valida la firma con la clave pública y rechaza los tokens vencidos.
	@Bean
	public JwtDecoder jwtDecoder(RSAPublicKey jwtPublicKey) {
		return NimbusJwtDecoder.withPublicKey(jwtPublicKey).build();
	}

}
