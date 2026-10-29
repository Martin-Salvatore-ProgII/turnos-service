package com.example.turnos.shared.infrastructure.web.validation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * Limita el tamaño de un texto en bytes (UTF-8). {@code @Size} cuenta caracteres, y una letra con
 * tilde o una "ñ" ocupan dos bytes.
 */
@Documented
@Constraint(validatedBy = MaxBytesValidator.class)
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface MaxBytes {

	int value();

	String message() default "must not exceed {value} bytes";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

}
