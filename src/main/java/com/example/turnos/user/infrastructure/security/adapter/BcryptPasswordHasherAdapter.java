package com.example.turnos.user.infrastructure.security.adapter;

import com.example.turnos.user.domain.ports.out.PasswordHasherPort;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class BcryptPasswordHasherAdapter implements PasswordHasherPort {

	// BCrypt: lento a propósito y con una sal aleatoria por contraseña, que queda dentro del
	// propio hash. Es el algoritmo que usa JHipster, cuyo modelo de usuario pide el enunciado.
	//
	// BCrypt no procesa contraseñas de más de 72 bytes: por eso el máximo del registro se
	// recortó a 72 bytes (HU-01) en lugar de los 100 caracteres que admite la cátedra. La
	// validación está en el registro; si llegara una más larga, encode lanza una excepción.
	private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

	@Override
	public String hash(String rawPassword) {
		return passwordEncoder.encode(rawPassword);
	}

	@Override
	public boolean matches(String rawPassword, String passwordHash) {
		return passwordEncoder.matches(rawPassword, passwordHash);
	}

}
