package com.example.turnos.reservation.domain.ports.in;

import com.example.turnos.reservation.domain.model.PageQuery;
import com.example.turnos.reservation.domain.model.PageResult;
import com.example.turnos.reservation.domain.model.ReservationProcess;
import com.example.turnos.reservation.domain.model.ReservationStatus;

public interface ListReservationsUseCase {

	// Las reservas del usuario del login (el del JWT), de la más reciente a la más antigua. Con status,
	// solo las que están en ese estado; null es sin filtro (HU-08, criterios 1 y 4).
	PageResult<ReservationProcess> listReservations(String login, ReservationStatus status, PageQuery page);

}
