package com.example.turnos.reservation.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.example.turnos.TestcontainersConfiguration;
import com.example.turnos.reservation.infrastructure.persistence.repository.JpaProcessedEventRepository;

// PostgreSQL real con las migraciones de Flyway (ADR-0045). Sin transacción de prueba: la regla
// "como máximo uno" tiene que valer entre transacciones distintas (ADR-0070), y una transacción de
// prueba que se deshace no mostraría la restricción de la base.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ TestcontainersConfiguration.class, JpaProcessedEventRepositoryAdapter.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class JpaProcessedEventRepositoryAdapterTest {

	private static final Instant NOW = Instant.parse("2026-11-02T15:00:00Z");

	@Autowired
	private JpaProcessedEventRepositoryAdapter adapter;

	@Autowired
	private JpaProcessedEventRepository jpaRepository;

	@AfterEach
	void cleanUp() {
		jpaRepository.deleteAllInBatch();
	}

	@Test
	void registersANewEvent() {
		assertThat(adapter.registerIfNew("evento-1", NOW)).isTrue();

		assertThat(jpaRepository.findById("evento-1").orElseThrow().getProcessedAt()).isEqualTo(NOW);
	}

	@Test
	void recognizesAnEventAlreadyRegisteredAndKeepsTheFirstDate() {
		adapter.registerIfNew("evento-1", NOW);

		assertThat(adapter.registerIfNew("evento-1", NOW.plusSeconds(60))).isFalse();

		assertThat(jpaRepository.count()).isEqualTo(1);
		assertThat(jpaRepository.findById("evento-1").orElseThrow().getProcessedAt()).isEqualTo(NOW);
	}

	@Test
	void keepsDifferentEventsApart() {
		assertThat(adapter.registerIfNew("evento-1", NOW)).isTrue();
		assertThat(adapter.registerIfNew("evento-2", NOW)).isTrue();
	}

	// ADR-0070: la base decide, no una consulta previa. Diez copias iguales a la vez: una sola gana.
	@Test
	void onlyOneOfManySimultaneousDuplicatesIsRegistered() throws Exception {
		int attempts = 10;
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(attempts);
		try {
			List<Future<Boolean>> results = new ArrayList<>();
			for (int i = 0; i < attempts; i++) {
				Callable<Boolean> attempt = () -> {
					start.await();
					return adapter.registerIfNew("evento-simultaneo", NOW);
				};
				results.add(executor.submit(attempt));
			}
			start.countDown();
			long registered = 0;
			for (Future<Boolean> result : results) {
				registered += result.get() ? 1 : 0;
			}

			assertThat(registered).isEqualTo(1);
			assertThat(jpaRepository.count()).isEqualTo(1);
		} finally {
			executor.shutdownNow();
		}
	}

	@Test
	void deletesOnlyTheEventsRegisteredBeforeTheCutoff() {
		adapter.registerIfNew("viejo", NOW.minusSeconds(100));
		adapter.registerIfNew("justo-en-el-limite", NOW);
		adapter.registerIfNew("nuevo", NOW.plusSeconds(100));

		int deleted = adapter.deleteProcessedBefore(NOW);

		assertThat(deleted).isEqualTo(1);
		assertThat(jpaRepository.findAll()).extracting("eventId").containsExactlyInAnyOrder("justo-en-el-limite",
				"nuevo");
	}

	@Test
	void anEventPurgedLongAgoCanBeRegisteredAgain() {
		adapter.registerIfNew("evento-1", NOW.minusSeconds(100));
		adapter.deleteProcessedBefore(NOW);

		assertThat(adapter.registerIfNew("evento-1", NOW)).isTrue();
	}

}
