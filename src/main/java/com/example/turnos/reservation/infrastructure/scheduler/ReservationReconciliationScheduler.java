package com.example.turnos.reservation.infrastructure.scheduler;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.example.turnos.reservation.application.service.ReservationService;
import com.example.turnos.shared.infrastructure.config.ReservationProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Disparador de la reconciliación (ADR-0030): corre al arrancar el servicio y después cada intervalo. No
 * tiene lógica de negocio: llama a la fachada. Con {@code turnos.reservation.reconciliation-enabled=false}
 * no existe, que es lo que usan las pruebas para que nada corra por su cuenta.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "turnos.reservation", name = "reconciliation-enabled", havingValue = "true",
		matchIfMissing = true)
public class ReservationReconciliationScheduler {

	private final ReservationService reservationService;
	private final ReservationProperties properties;

	// fixedDelay y no fixedRate: el siguiente ciclo empieza un intervalo después de que terminó el anterior,
	// así dos ciclos nunca se pisan. Sin initialDelay corre apenas arranca el servicio (CU-05).
	@Scheduled(fixedDelayString = "${turnos.reservation.reconciliation-interval}")
	public void reconcile() {
		try {
			reservationService.reconcileReservationProcesses(properties.reconciliationMargin());
			reservationService.purgeProcessedEvents(properties.processedEventRetention());
		} catch (RuntimeException e) {
			// El ciclo no lanza por un proceso ni por la cátedra; esto es, por ejemplo, la base caída. Se
			// registra y el siguiente ciclo lo reintenta: una excepción acá no tiene que frenar el scheduler.
			log.error("Reservation reconciliation cycle failed, it will run again at the next interval", e);
		}
	}

}
