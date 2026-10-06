package com.example.turnos.user.domain.ports.in;

import java.util.Optional;

public interface AuthenticateUserUseCase {

	// Vacío si las credenciales no son válidas, sin indicar el motivo.
	Optional<String> authenticateUser(String login, String rawPassword, boolean rememberMe);

}
