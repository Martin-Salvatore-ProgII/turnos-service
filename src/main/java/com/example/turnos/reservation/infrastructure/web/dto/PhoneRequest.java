package com.example.turnos.reservation.infrastructure.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// El cuerpo de POST /api/reservations/{id}/phone (docs/arq/contratos.md). Acá solo se valida que venga
// y que no sea desmesurado (RNF-05); el formato del teléfono es una regla del dominio (ADR-0027).
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PhoneRequest {

	@NotBlank
	@Size(max = 50)
	private String phoneNumber;

}
