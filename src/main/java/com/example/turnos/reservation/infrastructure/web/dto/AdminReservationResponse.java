package com.example.turnos.reservation.infrastructure.web.dto;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// La vista administrativa de una reserva: la misma representación que ve el dueño, más los datos de
// soporte (contratos.md, ADR-0037). Con @JsonUnwrapped los campos de la reserva salen al mismo nivel que
// los nuevos, y la representación común no se escribe dos veces.
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminReservationResponse {
	@JsonUnwrapped
	private ReservationResponse reservation;
	private ReservationOwnerResponse owner;
	private String reservationProcessId;
	// Login de quien canceló (el dueño o un administrador), CATEDRA si la cancelación vino de la cátedra,
	// o null si no está cancelada (modelo-db.md).
	private String cancelledBy;
}
