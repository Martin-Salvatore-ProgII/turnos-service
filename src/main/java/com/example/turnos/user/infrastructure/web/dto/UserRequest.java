package com.example.turnos.user.infrastructure.web.dto;

import com.example.turnos.shared.infrastructure.web.validation.MaxBytes;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Reglas de validación de HU-01. Solo tiene los campos que el cliente puede elegir: un id, un rol
// o un estado de activación enviados en el JSON no tienen dónde caer y se ignoran.
// Sin @ToString a propósito: la contraseña no tiene que poder terminar en un log.
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserRequest {

	// Patrón de login de JHipster: letras, números y algunos símbolos, o directamente un email.
	private static final String LOGIN_PATTERN =
			"^(?>[a-zA-Z0-9!$&*+=?^_`{|}~.-]+@[a-zA-Z0-9-]+(?:\\.[a-zA-Z0-9-]+)*)|(?>[_.@A-Za-z0-9-]+)$";

	@NotBlank(message = "Login is mandatory")
	@Size(max = 50, message = "Login must have at most 50 characters")
	@Pattern(regexp = LOGIN_PATTERN, message = "Login has invalid characters")
	private String login;

	// El máximo es de 72 bytes, y no de 100 caracteres, porque BCrypt no procesa más (HU-01).
	@NotNull(message = "Password is mandatory")
	@Size(min = 4, message = "Password must have at least 4 characters")
	@MaxBytes(value = 72, message = "Password must not exceed 72 bytes")
	private String password;

	@NotBlank(message = "First name is mandatory")
	@Size(min = 2, max = 50, message = "First name must have between 2 and 50 characters")
	private String firstName;

	@NotBlank(message = "Last name is mandatory")
	@Size(min = 2, max = 50, message = "Last name must have between 2 and 50 characters")
	private String lastName;

	@NotBlank(message = "Email is mandatory")
	@Email(message = "Email must be valid")
	@Size(min = 5, max = 254, message = "Email must have between 5 and 254 characters")
	private String email;

	@Size(max = 256, message = "Image URL must have at most 256 characters")
	private String imageUrl;

	@NotBlank(message = "Language key is mandatory")
	@Size(min = 2, max = 10, message = "Language key must have between 2 and 10 characters")
	private String langKey;
}
