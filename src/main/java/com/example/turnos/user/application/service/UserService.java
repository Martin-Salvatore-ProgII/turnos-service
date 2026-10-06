package com.example.turnos.user.application.service;

import java.util.Optional;

import com.example.turnos.user.application.exception.UserException;
import com.example.turnos.user.domain.model.User;
import com.example.turnos.user.domain.ports.in.AuthenticateUserUseCase;
import com.example.turnos.user.domain.ports.in.ProvisionAdminUserUseCase;
import com.example.turnos.user.domain.ports.in.RegisterUserUseCase;
import com.example.turnos.user.domain.ports.in.RevokeAdminRoleUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserService {

	private final RegisterUserUseCase registerUserUseCase;
	private final AuthenticateUserUseCase authenticateUserUseCase;
	private final ProvisionAdminUserUseCase provisionAdminUserUseCase;
	private final RevokeAdminRoleUseCase revokeAdminRoleUseCase;

	public User registerUser(User user, String rawPassword) {
		return registerUserUseCase.registerUser(user, rawPassword);
	}

	// El caso de uso no distingue entre login inexistente y contraseña incorrecta, y acá tampoco:
	// la respuesta no tiene que revelar cuál de los dos datos falló (HU-02).
	public String authenticateUser(String login, String rawPassword, boolean rememberMe) {
		return authenticateUserUseCase.authenticateUser(login, rawPassword, rememberMe)
				.orElseThrow(() -> new UserException(UserException.Code.INVALID_CREDENTIALS, "Invalid credentials"));
	}

	// Acá el resultado vacío no se traduce en una excepción: lo consume el arranque del servicio,
	// que tiene que poder informar el problema en el log y seguir funcionando sin administrador.
	public Optional<User> provisionAdminUser(User adminUser, String rawPassword) {
		return provisionAdminUserUseCase.provisionAdminUser(adminUser, rawPassword);
	}

	public void revokeAdminRole() {
		revokeAdminRoleUseCase.revokeAdminRole();
	}

}
