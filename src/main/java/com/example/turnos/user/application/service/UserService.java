package com.example.turnos.user.application.service;

import com.example.turnos.user.domain.model.User;
import com.example.turnos.user.domain.ports.in.RegisterUserUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserService {

	private final RegisterUserUseCase registerUserUseCase;

	public User registerUser(User user, String rawPassword) {
		return registerUserUseCase.registerUser(user, rawPassword);
	}

}
