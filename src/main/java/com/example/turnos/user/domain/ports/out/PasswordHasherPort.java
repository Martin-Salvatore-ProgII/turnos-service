package com.example.turnos.user.domain.ports.out;

/**
 * Hasheo de contraseñas. Es un puerto de salida porque el algoritmo es una tecnología externa:
 * los casos de uso dependen de esta interfaz y no de Spring Security, así se prueban con un mock
 * y cambiar de algoritmo solo toca al adaptador.
 */
public interface PasswordHasherPort {

	String hash(String rawPassword);

	boolean matches(String rawPassword, String passwordHash);

}
