package com.example.turnos.reservation.infrastructure.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// El cuerpo obligatorio de POST /api/admin/reservations/{id}/cancel (docs/arq/contratos.md). A diferencia
// del dueño, el administrador tiene que decir por qué cancela una reserva ajena (HU-10, criterio 4). El
// tope es el mismo que acepta la cátedra (REF §12).
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminCancelReservationRequest {

	@NotBlank
	@Size(max = 500)
	private String reason;

}
