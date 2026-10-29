package com.example.turnos;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;

/**
 * Genera en memoria un par de claves RSA para las pruebas y le pasa a la aplicación la ubicación
 * de la clave privada, igual que en producción se la pasa un archivo de {@code secrets/}. Así no
 * hay ninguna clave en el repositorio, ni siquiera de prueba.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestJwtKeysConfiguration {

	@Bean
	public DynamicPropertyRegistrar jwtKeyProperties() throws IOException, NoSuchAlgorithmException {
		Path privateKeyFile = Files.createTempFile("jwt-private-test", ".pem");
		privateKeyFile.toFile().deleteOnExit();
		Files.writeString(privateKeyFile, toPem(generateKeyPair()));
		return registry -> registry.add("turnos.security.jwt.private-key-location", () -> privateKeyFile.toUri().toString());
	}

	public static KeyPair generateKeyPair() throws NoSuchAlgorithmException {
		KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
		generator.initialize(2048);
		return generator.generateKeyPair();
	}

	private static String toPem(KeyPair keyPair) {
		String base64 = Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(keyPair.getPrivate().getEncoded());
		return "-----BEGIN PRIVATE KEY-----\n" + base64 + "\n-----END PRIVATE KEY-----\n";
	}

}
