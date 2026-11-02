package com.example.turnos.reservation.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

// La regla de la cátedra (REF §15.5, ADR-0027): se quitan espacios, guiones, paréntesis y puntos y lo que
// queda tiene que cumplir ^\+?[0-9]{7,15}$.
class PhoneNumberTest {

	@ParameterizedTest
	@CsvSource(delimiter = '|', textBlock = """
			+5492615551234|+5492615551234
			+54 9 261 555-1234|+5492615551234
			(261) 555.1234|2615551234
			  2615551234  |2615551234
			1234567|1234567
			+123456789012345|+123456789012345
			""")
	void normalizesAValidPhone(String raw, String expected) {
		assertThat(PhoneNumber.normalize(raw)).hasValue(expected);
	}

	@ParameterizedTest
	@ValueSource(strings = { "123456", "1234567890123456", "+1234567890123456", "abc1234567", "261-555-12ab", "++5492615551234",
			"54+92615551234", "", "   ", "-.()" })
	void rejectsAPhoneThatDoesNotMeetTheRuleAfterNormalizing(String raw) {
		assertThat(PhoneNumber.normalize(raw)).isEmpty();
	}

	@Test
	void rejectsNull() {
		assertThat(PhoneNumber.normalize(null)).isEmpty();
	}

}
