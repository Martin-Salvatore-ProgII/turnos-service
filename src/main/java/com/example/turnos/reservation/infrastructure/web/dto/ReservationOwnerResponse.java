package com.example.turnos.reservation.infrastructure.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// El campo owner de la vista administrativa de una reserva (contratos.md).
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReservationOwnerResponse {
	private String login;
	private String firstName;
	private String lastName;
}
