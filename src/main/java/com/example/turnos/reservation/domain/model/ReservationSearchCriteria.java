package com.example.turnos.reservation.domain.model;

import java.time.LocalDate;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Filtros del listado administrativo de reservas (HU-10, criterio 1). Todos opcionales: un filtro en null
// no restringe.
@Getter
@Setter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class ReservationSearchCriteria {
	// Login exacto del dueño, como lo pide el administrador.
	private String ownerLogin;
	// El id del dueño de ese login. No lo elige el administrador: lo completa el caso de uso con el puerto
	// de user, porque el usuario es de otra feature y la consulta de reservas no hace JOIN (ADR-0064).
	private Long ownerId;
	private ReservationStatus status;
	// Rango de fechas del turno, con los dos extremos incluidos. Puede venir uno solo.
	private LocalDate from;
	private LocalDate to;
}
