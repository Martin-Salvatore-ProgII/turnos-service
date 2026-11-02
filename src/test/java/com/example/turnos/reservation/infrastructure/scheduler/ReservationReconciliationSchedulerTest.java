package com.example.turnos.reservation.infrastructure.scheduler;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import com.example.turnos.reservation.application.service.ReservationService;
import com.example.turnos.shared.infrastructure.config.ReservationProperties;

// Sin Spring: se prueba qué le pide el disparador a la fachada, con qué valores y que un ciclo que falla no
// frene al scheduler.
class ReservationReconciliationSchedulerTest {

	private final ReservationService reservationService = mock(ReservationService.class);
	private final ReservationReconciliationScheduler scheduler = new ReservationReconciliationScheduler(
			reservationService,
			new ReservationProperties(Duration.ofDays(7), 3, Duration.ofSeconds(60), Duration.ofSeconds(30)));

	@Test
	void reconcilesWithTheConfiguredMarginAndThenPurgesWithTheConfiguredRetention() {
		scheduler.reconcile();

		InOrder order = inOrder(reservationService);
		order.verify(reservationService).reconcileReservationProcesses(Duration.ofSeconds(30));
		order.verify(reservationService).purgeProcessedEvents(Duration.ofDays(7));
	}

	@Test
	void aFailingCycleDoesNotEscapeSoTheSchedulerKeepsRunning() {
		when(reservationService.reconcileReservationProcesses(Duration.ofSeconds(30)))
				.thenThrow(new IllegalStateException("la base no responde"));

		scheduler.reconcile();

		verify(reservationService).reconcileReservationProcesses(Duration.ofSeconds(30));
	}

}
