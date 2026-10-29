package com.example.turnos.catalog.infrastructure.rest.dto;


import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@JsonIgnoreProperties(ignoreUnknown = true)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProblemDto {
	// Solo interesa el código funcional: los errores se deciden por status y code, nunca por el
	// texto de detail (contratos.md).
	private String code;
}
