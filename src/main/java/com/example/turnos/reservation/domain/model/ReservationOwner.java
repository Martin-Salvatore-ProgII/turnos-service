package com.example.turnos.reservation.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// El dueño de una reserva, como lo ve el administrador (contratos.md). Solo estos tres datos: el resto del
// usuario (email, hash de la contraseña, roles) no forma parte de la vista administrativa (ADR-0037).
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReservationOwner {
	private String login;
	private String firstName;
	private String lastName;
}
