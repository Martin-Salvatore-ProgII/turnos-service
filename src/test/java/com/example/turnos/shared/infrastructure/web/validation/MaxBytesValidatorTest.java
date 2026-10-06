package com.example.turnos.shared.infrastructure.web.validation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

class MaxBytesValidatorTest {

	private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

	@Test
	void acceptsTextUpToTheLimit() {
		assertThat(validate("abcd")).isEmpty();
	}

	@Test
	void rejectsTextOverTheLimit() {
		assertThat(validate("abcde")).hasSize(1);
	}

	@Test
	void countsBytesNotCharacters() {
		// "ñañ" son 3 caracteres pero 5 bytes en UTF-8.
		assertThat(validate("ñañ")).hasSize(1);
	}

	@Test
	void acceptsNull() {
		assertThat(validate(null)).isEmpty();
	}

	private Set<ConstraintViolation<Sample>> validate(String value) {
		return validator.validate(new Sample(value));
	}

	private record Sample(@MaxBytes(4) String value) {
	}

}
