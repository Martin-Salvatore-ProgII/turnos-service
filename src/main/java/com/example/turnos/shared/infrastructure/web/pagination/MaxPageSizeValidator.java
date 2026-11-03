package com.example.turnos.shared.infrastructure.web.pagination;

import com.example.turnos.shared.infrastructure.config.PaginationProperties;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import lombok.RequiredArgsConstructor;

// Spring crea el validador y le inyecta la configuración.
@RequiredArgsConstructor
public class MaxPageSizeValidator implements ConstraintValidator<MaxPageSize, Integer> {

	private final PaginationProperties paginationProperties;

	// Sin tamaño se usa el de por defecto: no hay nada que validar.
	@Override
	public boolean isValid(Integer size, ConstraintValidatorContext context) {
		return size == null || size <= paginationProperties.maxSize();
	}

}
