package com.example.turnos.shared.infrastructure.web.pagination;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.turnos.shared.infrastructure.config.PaginationProperties;
import org.junit.jupiter.api.Test;

class MaxPageSizeValidatorTest {

	// Un máximo distinto del de application.yaml: el límite sale de la configuración (RNF-09).
	private final MaxPageSizeValidator validator = new MaxPageSizeValidator(new PaginationProperties(10, 50));

	@Test
	void acceptsTheMaximum() {
		assertThat(validator.isValid(50, null)).isTrue();
	}

	@Test
	void rejectsMoreThanTheMaximum() {
		assertThat(validator.isValid(51, null)).isFalse();
	}

	@Test
	void acceptsAMissingSizeBecauseTheDefaultIsUsed() {
		assertThat(validator.isValid(null, null)).isTrue();
	}

}
