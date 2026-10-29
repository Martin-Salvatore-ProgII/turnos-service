package com.example.turnos.shared.infrastructure.web.advice;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class FieldErrorResponse {
	// Un parámetro de la ruta no pertenece a ningún objeto: sin objectName, el campo se omite en vez
	// de mandarse en null (el contrato lo define como texto opcional).
	@JsonInclude(JsonInclude.Include.NON_NULL)
	private String objectName;
	private String field;
	private String message;
}
