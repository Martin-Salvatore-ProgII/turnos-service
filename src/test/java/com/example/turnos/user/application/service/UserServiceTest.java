package com.example.turnos.user.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.Optional;

import com.example.turnos.user.application.exception.UserException;
import com.example.turnos.user.domain.model.User;
import com.example.turnos.user.domain.ports.in.AuthenticateUserUseCase;
import com.example.turnos.user.domain.ports.in.RegisterUserUseCase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

	@Mock
	private RegisterUserUseCase registerUserUseCase;

	@Mock
	private AuthenticateUserUseCase authenticateUserUseCase;

	@InjectMocks
	private UserService userService;

	@Test
	void registerUserDelegatesToTheUseCase() {
		User user = User.builder().login("juan").build();
		User registered = User.builder().id(1L).login("juan").build();
		when(registerUserUseCase.registerUser(user, "secret-1234")).thenReturn(registered);

		assertThat(userService.registerUser(user, "secret-1234")).isSameAs(registered);
	}

	@Test
	void authenticateUserReturnsTheToken() {
		when(authenticateUserUseCase.authenticateUser("juan", "secret-1234", false)).thenReturn(Optional.of("token"));

		assertThat(userService.authenticateUser("juan", "secret-1234", false)).isEqualTo("token");
	}

	@Test
	void authenticateUserTranslatesEmptyIntoInvalidCredentials() {
		when(authenticateUserUseCase.authenticateUser("juan", "otra-clave", false)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> userService.authenticateUser("juan", "otra-clave", false))
				.isInstanceOf(UserException.class)
				.extracting("code")
				.isEqualTo(UserException.Code.INVALID_CREDENTIALS);
	}

}
