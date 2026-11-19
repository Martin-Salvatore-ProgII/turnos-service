package com.example.turnos.reservation.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Una reserva con los datos de su dueño: lo que ve el administrador (HU-10, criterio 2).
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReservationWithOwner {
	private ReservationProcess process;
	private ReservationOwner owner;
}
