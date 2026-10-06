package com.example.turnos.user.infrastructure.web.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// El campo se llama "username", y no "login" como en el registro, porque así lo define el
// inicio de sesión de JHipster que el contrato replica. Sin @ToString: lleva la contraseña.
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginRequest {

	@NotBlank(message = "Username is mandatory")
	private String username;

	@NotBlank(message = "Password is mandatory")
	private String password;

	// Opcional: es Boolean y no boolean para que el pedido sea válido aunque el campo no venga.
	private Boolean rememberMe;
}
