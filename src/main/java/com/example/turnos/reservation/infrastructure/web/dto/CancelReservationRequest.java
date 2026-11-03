package com.example.turnos.reservation.infrastructure.web.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// El cuerpo opcional de POST /api/reservations/{id}/cancel (docs/arq/contratos.md). El motivo es
// opcional y tiene el mismo tope que acepta la cátedra (REF §12, ADR-0026).
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CancelReservationRequest {

	@Size(max = 500)
	private String reason;

}
