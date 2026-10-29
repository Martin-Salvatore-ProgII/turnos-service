package com.example.turnos.user.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.Set;

import com.example.turnos.user.domain.model.User;
import com.example.turnos.user.domain.ports.out.PasswordHasherPort;
import com.example.turnos.user.domain.ports.out.TokenIssuerPort;
import com.example.turnos.user.domain.ports.out.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuthenticateUserUseCaseImplTest {

	@Mock
	private UserRepository repository;

	@Mock
	private PasswordHasherPort passwordHasher;

	@Mock
	private TokenIssuerPort tokenIssuer;

	@InjectMocks
	private AuthenticateUserUseCaseImpl useCase;

	@Test
	void returnsTokenForValidCredentials() {
		User user = storedUser(true);
		when(repository.findByLogin("juan")).thenReturn(Optional.of(user));
		when(passwordHasher.matches("secret-1234", "hashed")).thenReturn(true);
		when(tokenIssuer.issue(user, false)).thenReturn("token");

		assertThat(useCase.authenticateUser("juan", "secret-1234", false)).contains("token");
	}

	@Test
	void looksUpTheLoginInLowercase() {
		User user = storedUser(true);
		when(repository.findByLogin("juan")).thenReturn(Optional.of(user));
		when(passwordHasher.matches("secret-1234", "hashed")).thenReturn(true);
		when(tokenIssuer.issue(user, false)).thenReturn("token");

		assertThat(useCase.authenticateUser("JUAN", "secret-1234", false)).contains("token");
	}

	@Test
	void passesRememberMeToTheTokenIssuer() {
		User user = storedUser(true);
		when(repository.findByLogin("juan")).thenReturn(Optional.of(user));
		when(passwordHasher.matches("secret-1234", "hashed")).thenReturn(true);
		when(tokenIssuer.issue(user, true)).thenReturn("long-token");

		assertThat(useCase.authenticateUser("juan", "secret-1234", true)).contains("long-token");
	}

	@Test
	void returnsEmptyForUnknownLogin() {
		when(repository.findByLogin("nadie")).thenReturn(Optional.empty());

		assertThat(useCase.authenticateUser("nadie", "secret-1234", false)).isEmpty();
		verify(tokenIssuer, never()).issue(any(), anyBoolean());
	}

	@Test
	void returnsEmptyForWrongPassword() {
		when(repository.findByLogin("juan")).thenReturn(Optional.of(storedUser(true)));
		when(passwordHasher.matches("otra-clave", "hashed")).thenReturn(false);

		assertThat(useCase.authenticateUser("juan", "otra-clave", false)).isEmpty();
		verify(tokenIssuer, never()).issue(any(), anyBoolean());
	}

	@Test
	void returnsEmptyForDeactivatedUser() {
		when(repository.findByLogin("juan")).thenReturn(Optional.of(storedUser(false)));

		assertThat(useCase.authenticateUser("juan", "secret-1234", false)).isEmpty();
		verify(tokenIssuer, never()).issue(any(), anyBoolean());
	}

	private User storedUser(boolean activated) {
		return User.builder()
				.id(1L)
				.login("juan")
				.passwordHash("hashed")
				.activated(activated)
				.authorities(Set.of("ROLE_USER"))
				.build();
	}

}
