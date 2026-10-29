package com.example.turnos.user.application.usecases;

import java.util.HashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.example.turnos.user.domain.model.User;
import com.example.turnos.user.domain.ports.in.ProvisionAdminUserUseCase;
import com.example.turnos.user.domain.ports.out.PasswordHasherPort;
import com.example.turnos.user.domain.ports.out.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProvisionAdminUserUseCaseImpl implements ProvisionAdminUserUseCase {

	private static final String ROLE_USER = "ROLE_USER";
	private static final String ROLE_ADMIN = "ROLE_ADMIN";

	private final UserRepository repository;
	private final PasswordHasherPort passwordHasher;

	@Override
	public Optional<User> provisionAdminUser(User adminUser, String rawPassword) {
		String login = adminUser.getLogin().trim().toLowerCase(Locale.ROOT);
		String email = adminUser.getEmail().trim().toLowerCase(Locale.ROOT);

		// La configuración manda: quien tenía el rol y no es el login configurado, lo pierde.
		repository.findAllByAuthority(ROLE_ADMIN).stream()
				.filter(user -> !user.getLogin().equals(login))
				.forEach(user -> repository.update(user.getId(), withoutAdminRole(user)));

		Optional<User> existing = repository.findByLogin(login);
		if (existing.isPresent()) {
			return keepAdminOnlyIfPasswordMatches(existing.get(), rawPassword);
		}
		if (repository.existsByEmail(email)) {
			return Optional.empty();
		}

		// El administrador no nace del registro público: lo crea el propio servicio con las
		// credenciales que le da quien lo opera. También es un usuario común (ADR-0061).
		User newAdmin = User.builder()
				.externalPatientId(UUID.randomUUID())
				.login(login)
				.passwordHash(passwordHasher.hash(rawPassword))
				.firstName(adminUser.getFirstName())
				.lastName(adminUser.getLastName())
				.email(email)
				.activated(true)
				.langKey(adminUser.getLangKey())
				.authorities(Set.of(ROLE_USER, ROLE_ADMIN))
				.build();
		return Optional.of(repository.create(newAdmin));
	}

	// El registro es público: una cuenta con el login configurado pudo haberla creado cualquiera.
	// Solo es administrador si además tiene la contraseña configurada, que nadie más conoce.
	// Si no coincide, no se le da el rol, y si lo tenía se le quita.
	private Optional<User> keepAdminOnlyIfPasswordMatches(User user, String rawPassword) {
		boolean isAdmin = user.getAuthorities().contains(ROLE_ADMIN);
		if (passwordHasher.matches(rawPassword, user.getPasswordHash())) {
			return isAdmin ? Optional.of(user) : repository.update(user.getId(), withAdminRole(user));
		}
		if (isAdmin) {
			repository.update(user.getId(), withoutAdminRole(user));
		}
		return Optional.empty();
	}

	private User withAdminRole(User user) {
		Set<String> authorities = new HashSet<>(user.getAuthorities());
		authorities.add(ROLE_ADMIN);
		user.setAuthorities(authorities);
		return user;
	}

	private User withoutAdminRole(User user) {
		Set<String> authorities = new HashSet<>(user.getAuthorities());
		authorities.remove(ROLE_ADMIN);
		user.setAuthorities(authorities);
		return user;
	}

}
