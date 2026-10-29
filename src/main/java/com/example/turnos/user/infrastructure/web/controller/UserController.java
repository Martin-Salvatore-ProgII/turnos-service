package com.example.turnos.user.infrastructure.web.controller;

import com.example.turnos.user.application.service.UserService;
import com.example.turnos.user.domain.model.User;
import com.example.turnos.user.infrastructure.web.dto.LoginRequest;
import com.example.turnos.user.infrastructure.web.dto.TokenResponse;
import com.example.turnos.user.infrastructure.web.dto.UserRequest;
import com.example.turnos.user.infrastructure.web.mapper.UserDtoMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class UserController {

	private final UserService userService;
	private final UserDtoMapper userDtoMapper;

	// Responde 201 sin cuerpo, como el registro de JHipster: no hay nada del usuario que devolver.
	@PostMapping("/register")
	public ResponseEntity<Void> registerUser(@Valid @RequestBody UserRequest userRequest) {
		User user = userDtoMapper.toDomain(userRequest);
		userService.registerUser(user, userRequest.getPassword());
		return new ResponseEntity<>(HttpStatus.CREATED);
	}

	@PostMapping("/authenticate")
	public ResponseEntity<TokenResponse> authenticateUser(@Valid @RequestBody LoginRequest loginRequest) {
		boolean rememberMe = Boolean.TRUE.equals(loginRequest.getRememberMe());
		String token = userService.authenticateUser(loginRequest.getUsername(), loginRequest.getPassword(), rememberMe);
		return ResponseEntity.ok(userDtoMapper.toResponse(token));
	}

}
