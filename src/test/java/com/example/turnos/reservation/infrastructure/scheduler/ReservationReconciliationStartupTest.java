package com.example.turnos.reservation.infrastructure.scheduler;

import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.example.turnos.TestCatalogConfiguration;
import com.example.turnos.TestCatedraConfiguration;
import com.example.turnos.TestJwtKeysConfiguration;
import com.example.turnos.TestcontainersConfiguration;
import com.example.turnos.reservation.application.service.ReservationService;

// La reconciliación corre al arrancar el servicio (CU-05, ADR-0030), con el disparador real y la
// configuración que usa el servicio. El intervalo es de una hora: lo que se ve es la corrida del arranque.
// La fachada es un mock; lo que hace cada ciclo se prueba en ReconcileReservationProcessesUseCaseImplTest.
@SpringBootTest(properties = { "turnos.reservation.reconciliation-enabled=true",
		"turnos.reservation.reconciliation-interval=1h" })
@Import({ TestcontainersConfiguration.class, TestJwtKeysConfiguration.class, TestCatedraConfiguration.class,
		TestCatalogConfiguration.class })
class ReservationReconciliationStartupTest {

	@MockitoBean
	private ReservationService reservationService;

	@Test
	void reconcilesAndPurgesRightAfterStartingWithTheConfiguredValues() {
		verify(reservationService, timeout(10_000)).reconcileReservationProcesses(Duration.ofSeconds(30));
		verify(reservationService, timeout(10_000)).purgeProcessedEvents(Duration.ofDays(7));
	}

}
