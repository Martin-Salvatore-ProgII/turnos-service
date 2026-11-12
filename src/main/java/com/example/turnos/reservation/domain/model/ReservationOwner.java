package com.example.turnos.reservation.domain.model;

import com.example.turnos.user.domain.model.User;
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

	// Lo usan el listado y el detalle administrativos. Un usuario que no existe es un dueño desconocido.
	public static ReservationOwner of(User user) {
		if (user == null) {
			return null;
		}
		return ReservationOwner.builder()
				.login(user.getLogin())
				.firstName(user.getFirstName())
				.lastName(user.getLastName())
				.build();
	}
}
