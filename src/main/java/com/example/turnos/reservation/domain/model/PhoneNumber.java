package com.example.turnos.reservation.domain.model;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * La regla del teléfono de la cátedra (REF §15.5, ADR-0027): se quitan espacios, guiones, paréntesis y
 * puntos, y lo que queda tiene que cumplir {@code ^\+?[0-9]{7,15}$}. Se aplica antes de publicar, para
 * rechazar al instante lo que la cátedra rechazaría después de un ida y vuelta por Kafka.
 */
public final class PhoneNumber {

	private static final Pattern SEPARATORS = Pattern.compile("[\\s\\-().]");
	private static final Pattern VALID = Pattern.compile("^\\+?[0-9]{7,15}$");

	private PhoneNumber() {
	}

	/** El teléfono normalizado, o vacío si no cumple la regla. Es lo que se publica. */
	public static Optional<String> normalize(String raw) {
		if (raw == null) {
			return Optional.empty();
		}
		String normalized = SEPARATORS.matcher(raw).replaceAll("");
		return VALID.matcher(normalized).matches() ? Optional.of(normalized) : Optional.empty();
	}

}
