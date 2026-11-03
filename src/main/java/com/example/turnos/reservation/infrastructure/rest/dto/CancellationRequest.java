package com.example.turnos.reservation.infrastructure.rest.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Cuerpo de POST /api/appointments/{reservationProcessId}/cancel (REF §12). Es opcional; reason admite
// hasta 500 caracteres, que el pedido del usuario ya valida (contratos.md).
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CancellationRequest {
	private String reason;
}
