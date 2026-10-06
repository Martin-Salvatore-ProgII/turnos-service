package com.example.turnos.shared.infrastructure.web.validation;

import java.nio.charset.StandardCharsets;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class MaxBytesValidator implements ConstraintValidator<MaxBytes, String> {

	private int maxBytes;

	@Override
	public void initialize(MaxBytes constraint) {
		this.maxBytes = constraint.value();
	}

	@Override
	public boolean isValid(String value, ConstraintValidatorContext context) {
		// Un valor nulo no es problema de esta validación: de eso se ocupa @NotNull.
		return value == null || value.getBytes(StandardCharsets.UTF_8).length <= maxBytes;
	}

}
