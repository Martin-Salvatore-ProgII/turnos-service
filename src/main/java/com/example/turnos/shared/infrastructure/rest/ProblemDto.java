package com.example.turnos.shared.infrastructure.rest;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.web.client.RestClientResponseException;

/**
 * El cuerpo {@code problem+json} de un error del catálogo o de la cátedra, que usan la misma forma
 * (REF §13, contratos.md). Lo comparten los clientes de los dos servicios (ADR-0063).
 */
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

	// El code de una respuesta de error, o null si el cuerpo no lo trae o no se puede leer: un error
	// sin el código del contrato (por ejemplo, de una ruta equivocada) no prueba nada sobre el pedido.
	public static String codeOf(RestClientResponseException e) {
		try {
			ProblemDto problem = e.getResponseBodyAs(ProblemDto.class);
			return problem == null ? null : problem.getCode();
		} catch (RuntimeException unreadable) {
			return null;
		}
	}
}
