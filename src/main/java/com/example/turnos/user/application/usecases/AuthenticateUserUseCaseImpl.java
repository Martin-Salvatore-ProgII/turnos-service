package com.example.turnos.user.application.usecases;

import java.util.Locale;
import java.util.Optional;

import com.example.turnos.user.domain.model.User;
import com.example.turnos.user.domain.ports.in.AuthenticateUserUseCase;
import com.example.turnos.user.domain.ports.out.PasswordHasherPort;
import com.example.turnos.user.domain.ports.out.TokenIssuerPort;
import com.example.turnos.user.domain.ports.out.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AuthenticateUserUseCaseImpl implements AuthenticateUserUseCase {

	private final UserRepository repository;
	private final PasswordHasherPort passwordHasher;
	private final TokenIssuerPort tokenIssuer;

	@Override
	public Optional<String> authenticateUser(String login, String rawPassword, boolean rememberMe) {
		// El login se guardó en minúsculas al registrarse, así que se busca igual.
		return repository.findByLogin(login.toLowerCase(Locale.ROOT))
				.filter(User::isActivated)
				.filter(user -> passwordHasher.matches(rawPassword, user.getPasswordHash()))
				.map(user -> tokenIssuer.issue(user, rememberMe));
	}

}
