package com.example.turnos.shared.infrastructure.web.pagination;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * El tamaño de página no supera el máximo configurado ({@code turnos.pagination.max-size}). Un
 * {@code @Max} no sirve: su valor se fija al compilar, y el máximo es configurable (RNF-09).
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = MaxPageSizeValidator.class)
public @interface MaxPageSize {

	String message() default "must be less than or equal to the maximum page size";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

}
