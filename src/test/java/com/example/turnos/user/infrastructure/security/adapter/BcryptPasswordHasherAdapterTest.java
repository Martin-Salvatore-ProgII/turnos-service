package com.example.turnos.user.infrastructure.security.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class BcryptPasswordHasherAdapterTest {

	private final BcryptPasswordHasherAdapter passwordHasher = new BcryptPasswordHasherAdapter();

	@Test
	void hashIsNotTheRawPassword() {
		String hash = passwordHasher.hash("secret-1234");

		assertThat(hash).isNotEqualTo("secret-1234").doesNotContain("secret-1234");
	}

	@Test
	void matchesTheOriginalPassword() {
		String hash = passwordHasher.hash("secret-1234");

		assertThat(passwordHasher.matches("secret-1234", hash)).isTrue();
	}

	@Test
	void doesNotMatchADifferentPassword() {
		String hash = passwordHasher.hash("secret-1234");

		assertThat(passwordHasher.matches("secret-12345", hash)).isFalse();
	}

	@Test
	void samePasswordProducesDifferentHashes() {
		assertThat(passwordHasher.hash("secret-1234")).isNotEqualTo(passwordHasher.hash("secret-1234"));
	}

	@Test
	void hashesTheLongestPasswordAllowed() {
		String longest = "a".repeat(72);

		assertThat(passwordHasher.matches(longest, passwordHasher.hash(longest))).isTrue();
	}

	@Test
	void rejectsPasswordLongerThan72Bytes() {
		assertThatThrownBy(() -> passwordHasher.hash("a".repeat(73)))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void measuresTheLimitInBytesNotCharacters() {
		// 37 letras "ñ" son 37 caracteres pero 74 bytes en UTF-8.
		assertThatThrownBy(() -> passwordHasher.hash("ñ".repeat(37)))
				.isInstanceOf(IllegalArgumentException.class);
	}

}
