package com.example.turnos.user.application.service;

import com.example.turnos.user.application.exception.UserException;
import com.example.turnos.user.domain.model.User;
import com.example.turnos.user.domain.ports.in.AuthenticateUserUseCase;
import com.example.turnos.user.domain.ports.in.RegisterUserUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserService {

	private final RegisterUserUseCase registerUserUseCase;
	private final AuthenticateUserUseCase authenticateUserUseCase;

	public User registerUser(User user, String rawPassword) {
		return registerUserUseCase.registerUser(user, rawPassword);
	}

	// El caso de uso no distingue entre login inexistente y contraseña incorrecta, y acá tampoco:
	// la respuesta no tiene que revelar cuál de los dos datos falló (HU-02).
	public String authenticateUser(String login, String rawPassword, boolean rememberMe) {
		return authenticateUserUseCase.authenticateUser(login, rawPassword, rememberMe)
				.orElseThrow(() -> new UserException(UserException.Code.INVALID_CREDENTIALS, "Invalid credentials"));
	}

}
