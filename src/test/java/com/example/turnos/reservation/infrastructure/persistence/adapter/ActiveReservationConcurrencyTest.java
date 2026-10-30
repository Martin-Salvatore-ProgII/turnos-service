package com.example.turnos.reservation.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import com.example.turnos.TestcontainersConfiguration;
import com.example.turnos.reservation.application.exception.ReservationException;
import com.example.turnos.reservation.domain.model.ReservationProcess;
import com.example.turnos.reservation.domain.model.ReservationStatus;
import com.example.turnos.reservation.infrastructure.persistence.mapper.ReservationProcessMapper;
import com.example.turnos.shared.infrastructure.config.JpaAuditingConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

// El doble toque en "Reservar" de verdad (ADR-0024, ADR-0070): dos hilos guardan a la vez un proceso
// activo del mismo usuario, cada uno en su propia transacción, como dos pedidos HTTP. Sin una
// transacción alrededor del test, para que cada guardado se confirme como en producción; por eso los
// datos se borran a mano al terminar.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({ TestcontainersConfiguration.class, JpaAuditingConfig.class, JpaReservationProcessRepositoryAdapter.class,
		ReservationProcessMapper.class })
class ActiveReservationConcurrencyTest {

	private static final String LOGIN = "concurrente";

	@Autowired
	private JpaReservationProcessRepositoryAdapter adapter;

	@Autowired
	private DataSource dataSource;

	private JdbcClient jdbcClient;
	private Long userId;

	@BeforeEach
	void insertOwner() {
		jdbcClient = JdbcClient.create(dataSource);
		jdbcClient.sql("""
				INSERT INTO app_user (external_patient_id, login, password_hash, first_name, last_name, email,
						activated, lang_key, created_by, created_date)
				VALUES (gen_random_uuid(), :login, 'hash', 'Juan', 'Perez', 'concurrente@example.com', true, 'es', 'test', now())
				""").param("login", LOGIN).update();
		userId = jdbcClient.sql("SELECT id FROM app_user WHERE login = :login").param("login", LOGIN)
				.query(Long.class).single();
	}

	@AfterEach
	void deleteData() {
		jdbcClient.sql("DELETE FROM reservation_process WHERE user_id = :userId").param("userId", userId).update();
		jdbcClient.sql("DELETE FROM app_user WHERE id = :userId").param("userId", userId).update();
	}

	@Test
	void twoSimultaneousReservationsOfTheSameUserLeaveExactlyOneActive() throws Exception {
		// La barrera suelta a los dos hilos juntos, para que ninguno guarde antes de que el otro empiece.
		CyclicBarrier together = new CyclicBarrier(2);
		ExecutorService executor = Executors.newFixedThreadPool(2);
		List<Future<String>> results = new ArrayList<>();
		for (int i = 0; i < 2; i++) {
			results.add(executor.submit(() -> {
				together.await(5, TimeUnit.SECONDS);
				try {
					adapter.create(startedProcess());
					return "created";
				} catch (ReservationException e) {
					return e.getCode().name();
				}
			}));
		}
		executor.shutdown();

		List<String> outcomes = new ArrayList<>();
		for (Future<String> result : results) {
			outcomes.add(result.get(10, TimeUnit.SECONDS));
		}

		assertThat(outcomes).containsExactlyInAnyOrder("created", "ACTIVE_RESERVATION_EXISTS");
		assertThat(jdbcClient.sql("SELECT count(*) FROM reservation_process WHERE user_id = :userId")
				.param("userId", userId).query(Long.class).single()).isEqualTo(1);
	}

	private ReservationProcess startedProcess() {
		return ReservationProcess.builder()
				.id(UUID.randomUUID())
				.userId(userId)
				.status(ReservationStatus.STARTED)
				.professionalId(15L)
				.professionalFirstName("Ana")
				.professionalLastName("Gomez")
				.appointmentDate(LocalDate.of(2026, 11, 16))
				.startTime(LocalTime.of(10, 0))
				.endTime(LocalTime.of(10, 30))
				.build();
	}

}
