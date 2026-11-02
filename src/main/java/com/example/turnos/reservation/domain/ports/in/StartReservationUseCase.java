package com.example.turnos.reservation.domain.ports.in;

import java.time.LocalDate;
import java.time.LocalTime;

import com.example.turnos.reservation.domain.model.ReservationProcess;

public interface StartReservationUseCase {

	// Inicia la reserva del slot para el usuario del login (el del JWT, nunca uno enviado por el
	// cliente). Devuelve el proceso guardado, en el estado en que quedó (CU-04).
	ReservationProcess startReservation(String login, Long professionalId, LocalDate date, LocalTime startTime);

}
