package com.example.turnos.reservation.infrastructure.rest.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Pedido de POST /api/appointment-holds/{holdId}/confirm (REF §10). Todos los campos son obligatorios;
// el nombre y el apellido van de 2 a 100 caracteres, que el registro ya garantiza (HU-01).
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConfirmationRequest {
	private String externalPatientId;
	private String patientFirstName;
	private String patientLastName;
}
