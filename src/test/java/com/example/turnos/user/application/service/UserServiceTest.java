package com.example.turnos.user.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.example.turnos.user.domain.model.User;
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

	@InjectMocks
	private UserService userService;

	@Test
	void registerUserDelegatesToTheUseCase() {
		User user = User.builder().login("juan").build();
		User registered = User.builder().id(1L).login("juan").build();
		when(registerUserUseCase.registerUser(user, "secret-1234")).thenReturn(registered);

		assertThat(userService.registerUser(user, "secret-1234")).isSameAs(registered);
	}

}
