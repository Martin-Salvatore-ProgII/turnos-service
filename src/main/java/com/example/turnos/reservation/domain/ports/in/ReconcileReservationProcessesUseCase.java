package com.example.turnos.reservation.domain.ports.in;

import java.time.Duration;

public interface ReconcileReservationProcessesUseCase {

	// Revisa los procesos que quedaron colgados y los lleva al estado que corresponde (CU-05, ADR-0030).
	// El margen es cuánto después del vencimiento (o de la creación, en STARTED) se los considera
	// colgados. Nunca lanza por un proceso o por la cátedra: lo que no se pudo resolver queda para el
	// próximo ciclo. Devuelve cuántos procesos cambió.
	int reconcileReservationProcesses(Duration margin);

}
