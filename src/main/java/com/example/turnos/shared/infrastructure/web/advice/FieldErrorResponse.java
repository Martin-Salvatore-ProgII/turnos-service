package com.example.turnos.shared.infrastructure.web.advice;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class FieldErrorResponse {
	private String objectName;
	private String field;
	private String message;
}
