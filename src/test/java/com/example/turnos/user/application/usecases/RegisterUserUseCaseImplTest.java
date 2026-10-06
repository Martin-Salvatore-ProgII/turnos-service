package com.example.turnos.user.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Set;

import com.example.turnos.user.application.exception.UserException;
import com.example.turnos.user.domain.model.User;
import com.example.turnos.user.domain.ports.out.PasswordHasherPort;
import com.example.turnos.user.domain.ports.out.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RegisterUserUseCaseImplTest {

	@Mock
	private UserRepository repository;

	@Mock
	private PasswordHasherPort passwordHasher;

	@InjectMocks
	private RegisterUserUseCaseImpl useCase;

	@Test
	void registersUserWithBackendAssignedData() {
		when(passwordHasher.hash("secret-1234")).thenReturn("hashed");
		when(repository.create(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

		useCase.registerUser(validUser(), "secret-1234");

		User saved = savedUser();
		assertThat(saved.getPasswordHash()).isEqualTo("hashed");
		assertThat(saved.getExternalPatientId()).isNotNull();
		assertThat(saved.isActivated()).isTrue();
		assertThat(saved.getAuthorities()).containsExactly("ROLE_USER");
		assertThat(saved.getFirstName()).isEqualTo("Juan");
		assertThat(saved.getLastName()).isEqualTo("Perez");
		assertThat(saved.getImageUrl()).isEqualTo("https://example.com/juan.png");
		assertThat(saved.getLangKey()).isEqualTo("es");
	}

	@Test
	void storesLoginAndEmailInLowercase() {
		when(repository.create(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
		User user = validUser();
		user.setLogin("Juan.Perez");
		user.setEmail("Juan@Example.com");

		useCase.registerUser(user, "secret-1234");

		User saved = savedUser();
		assertThat(saved.getLogin()).isEqualTo("juan.perez");
		assertThat(saved.getEmail()).isEqualTo("juan@example.com");
		verify(repository).existsByLogin("juan.perez");
		verify(repository).existsByEmail("juan@example.com");
	}

	@Test
	void ignoresIdAuthoritiesAndActivationSentByTheClient() {
		when(repository.create(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
		User user = validUser();
		user.setId(99L);
		user.setAuthorities(Set.of("ROLE_ADMIN"));
		user.setActivated(false);

		useCase.registerUser(user, "secret-1234");

		User saved = savedUser();
		assertThat(saved.getId()).isNull();
		assertThat(saved.getAuthorities()).containsExactly("ROLE_USER");
		assertThat(saved.isActivated()).isTrue();
	}

	@Test
	void rejectsLoginAlreadyInUse() {
		when(repository.existsByLogin("juan")).thenReturn(true);

		assertThatThrownBy(() -> useCase.registerUser(validUser(), "secret-1234"))
				.isInstanceOf(UserException.class)
				.extracting("code")
				.isEqualTo(UserException.Code.USERNAME_ALREADY_EXISTS);

		verify(repository, never()).create(any());
		verify(passwordHasher, never()).hash(any());
	}

	@Test
	void rejectsEmailAlreadyInUse() {
		when(repository.existsByEmail("juan@example.com")).thenReturn(true);

		assertThatThrownBy(() -> useCase.registerUser(validUser(), "secret-1234"))
				.isInstanceOf(UserException.class)
				.extracting("code")
				.isEqualTo(UserException.Code.EMAIL_ALREADY_EXISTS);

		verify(repository, never()).create(any());
	}

	private User validUser() {
		return User.builder()
				.login("juan")
				.firstName("Juan")
				.lastName("Perez")
				.email("juan@example.com")
				.imageUrl("https://example.com/juan.png")
				.langKey("es")
				.build();
	}

	private User savedUser() {
		ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
		verify(repository).create(captor.capture());
		return captor.getValue();
	}

}
