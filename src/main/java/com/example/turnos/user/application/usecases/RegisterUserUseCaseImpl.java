package com.example.turnos.user.application.usecases;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import com.example.turnos.user.application.exception.UserException;
import com.example.turnos.user.domain.model.User;
import com.example.turnos.user.domain.ports.in.RegisterUserUseCase;
import com.example.turnos.user.domain.ports.out.PasswordHasherPort;
import com.example.turnos.user.domain.ports.out.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RegisterUserUseCaseImpl implements RegisterUserUseCase {

	private static final String ROLE_USER = "ROLE_USER";

	private final UserRepository repository;
	private final PasswordHasherPort passwordHasher;

	@Override
	public User registerUser(User user, String rawPassword) {
		// login y email se guardan en minúsculas, así "Juan" y "juan" son el mismo usuario (HU-01).
		String login = user.getLogin().toLowerCase(Locale.ROOT);
		String email = user.getEmail().toLowerCase(Locale.ROOT);

		// Se consulta antes de guardar para poder decir cuál de los dos datos está repetido.
		// La restricción única de la base queda como red de seguridad ante dos registros simultáneos.
		if (repository.existsByLogin(login)) {
			throw new UserException(UserException.Code.USERNAME_ALREADY_EXISTS, "Login already in use");
		}
		if (repository.existsByEmail(email)) {
			throw new UserException(UserException.Code.EMAIL_ALREADY_EXISTS, "Email already in use");
		}

		// Se arma un usuario nuevo solo con los datos que el cliente puede elegir. El id, el UUID,
		// la activación y el rol los asigna el backend, aunque el cliente los haya enviado (HU-01).
		User newUser = User.builder()
				.externalPatientId(UUID.randomUUID())
				.login(login)
				.passwordHash(passwordHasher.hash(rawPassword))
				.firstName(user.getFirstName())
				.lastName(user.getLastName())
				.email(email)
				.imageUrl(user.getImageUrl())
				.activated(true)
				.langKey(user.getLangKey())
				.authorities(Set.of(ROLE_USER))
				.build();

		return repository.create(newUser);
	}

}
