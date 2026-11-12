package com.example.turnos.reservation.domain.ports.in;

import com.example.turnos.reservation.domain.model.PageQuery;
import com.example.turnos.reservation.domain.model.PageResult;
import com.example.turnos.reservation.domain.model.ReservationSearchCriteria;
import com.example.turnos.reservation.domain.model.ReservationWithOwner;

public interface ListAllReservationsUseCase {

	// Las reservas de todos los usuarios, con su dueño, de la más reciente a la más antigua (HU-10,
	// criterio 1). Solo para el administrador: lo garantiza la ruta (ADR-0037), no este caso de uso.
	PageResult<ReservationWithOwner> listAllReservations(ReservationSearchCriteria criteria, PageQuery page);

}
