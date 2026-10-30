package com.example.turnos.reservation.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import com.example.turnos.TestcontainersConfiguration;
import com.example.turnos.reservation.application.exception.ReservationException;
import com.example.turnos.reservation.application.exception.ReservationProcessConflictException;
import com.example.turnos.reservation.domain.model.ReservationProcess;
import com.example.turnos.reservation.domain.model.ReservationStatus;
import com.example.turnos.reservation.infrastructure.persistence.entity.ReservationProcessEntity;
import com.example.turnos.reservation.infrastructure.persistence.mapper.ReservationProcessMapper;
import com.example.turnos.shared.infrastructure.config.JpaAuditingConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

// Prueba el adaptador contra PostgreSQL real, con las migraciones de Flyway (ADR-0045).
// Cada test corre en una transacción que se deshace al terminar.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ TestcontainersConfiguration.class, JpaAuditingConfig.class, JpaReservationProcessRepositoryAdapter.class,
		ReservationProcessMapper.class })
class JpaReservationProcessRepositoryAdapterTest {

	@Autowired
	private JpaReservationProcessRepositoryAdapter adapter;

	@Autowired
	private ReservationProcessMapper mapper;

	@Autowired
	private TestEntityManager entityManager;

	private Long userId;

	// El proceso necesita un dueño existente (clave foránea a app_user). Se inserta con SQL para no
	// depender del adaptador de otra feature.
	@BeforeEach
	void insertOwner() {
		userId = insertUser("juan");
	}

	private Long insertUser(String login) {
		entityManager.getEntityManager().createNativeQuery("""
				INSERT INTO app_user (external_patient_id, login, password_hash, first_name, last_name, email,
						activated, lang_key, created_by, created_date)
				VALUES (gen_random_uuid(), :login, 'hash', 'Juan', 'Perez', :login || '@example.com', true, 'es', 'test', now())
				""").setParameter("login", login).executeUpdate();
		return ((Number) entityManager.getEntityManager()
				.createNativeQuery("SELECT id FROM app_user WHERE login = :login")
				.setParameter("login", login)
				.getSingleResult()).longValue();
	}

	@Test
	void createPersistsEveryFieldAndReadsItBack() {
		ReservationProcess process = fullProcess();

		adapter.create(process);
		// Se vacía la memoria de JPA para que la lectura venga de verdad de PostgreSQL.
		entityManager.flush();
		entityManager.clear();

		ReservationProcess found = read(process.getId());
		assertThat(found).usingRecursiveComparison()
				.ignoringFields("createdAt", "updatedAt", "version")
				.isEqualTo(process);
	}

	@Test
	void createFillsTheAuditDatesAndStartsTheVersion() {
		ReservationProcess created = adapter.create(startedProcess());

		assertThat(created.getCreatedAt()).isNotNull();
		assertThat(created.getUpdatedAt()).isNotNull();
		assertThat(created.getVersion()).isZero();
	}

	@Test
	void updateSavesTheChangesAndIncreasesTheVersion() {
		// Se parte del proceso leído de la base: PostgreSQL guarda las horas con microsegundos y Java
		// las tiene con nanosegundos, así que la copia en memoria no sirve para comparar createdAt.
		UUID id = adapter.create(startedProcess()).getId();
		entityManager.clear();
		ReservationProcess created = read(id);
		entityManager.clear();

		created.setStatus(ReservationStatus.HELD);
		created.setHoldId("hold-1");
		created.setReservationProcessId("process-1");
		ReservationProcess updated = adapter.update(created.getId(), created).orElseThrow();
		entityManager.clear();

		assertThat(updated.getVersion()).isEqualTo(1);
		ReservationProcess found = read(created.getId());
		assertThat(found.getStatus()).isEqualTo(ReservationStatus.HELD);
		assertThat(found.getHoldId()).isEqualTo("hold-1");
		assertThat(found.getReservationProcessId()).isEqualTo("process-1");
		assertThat(found.getCreatedAt()).isEqualTo(created.getCreatedAt());
	}

	@Test
	void updateOverAnOldReadIsRejectedAndKeepsTheNewerChange() {
		// El ejemplo de ADR-0068: dos copias leídas en la misma versión; guarda primero una y después
		// la otra, que ya está vieja.
		ReservationProcess created = adapter.create(startedProcess());
		entityManager.clear();
		ReservationProcess first = read(created.getId());
		ReservationProcess stale = read(created.getId());

		first.setStatus(ReservationStatus.HELD);
		adapter.update(first.getId(), first);
		entityManager.clear();

		stale.setStatus(ReservationStatus.FAILED);
		assertThatThrownBy(() -> adapter.update(stale.getId(), stale))
				.isInstanceOf(ReservationProcessConflictException.class);
		entityManager.clear();
		assertThat(read(created.getId()).getStatus()).isEqualTo(ReservationStatus.HELD);
	}

	@Test
	void updateReturnsEmptyForUnknownId() {
		ReservationProcess process = startedProcess();

		assertThat(adapter.update(process.getId(), process)).isEmpty();
	}

	@Test
	void databaseRejectsADuplicatedReservationProcessId() {
		// Procesos ya terminados, para que no los rechace el índice de un proceso activo por usuario.
		ReservationProcess first = finishedProcess();
		first.setReservationProcessId("process-1");
		adapter.create(first);
		ReservationProcess second = finishedProcess();
		second.setReservationProcessId("process-1");

		assertThatThrownBy(() -> adapter.create(second)).isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void databaseAcceptsManyProcessesWithoutReservationProcessId() {
		// Antes del hold no hay reservationProcessId: la restricción UNIQUE admite varios NULL.
		adapter.create(finishedProcess());
		adapter.create(finishedProcess());
		entityManager.flush();
	}

	@Test
	void databaseRejectsASecondActiveProcessOfTheSameUser() {
		// create no consulta antes: el rechazo es de la base, que es lo que vale ante pedidos
		// simultáneos (ADR-0070). Se traduce al error funcional.
		adapter.create(startedProcess());

		assertThatThrownBy(() -> adapter.create(startedProcess()))
				.isInstanceOf(ReservationException.class)
				.extracting("code").isEqualTo(ReservationException.Code.ACTIVE_RESERVATION_EXISTS);
	}

	@Test
	void aFinishedProcessDoesNotBlockANewOne() {
		ReservationProcess first = adapter.create(startedProcess());
		entityManager.clear();
		first = read(first.getId());
		first.setStatus(ReservationStatus.FAILED);
		adapter.update(first.getId(), first);

		ReservationProcess second = adapter.create(startedProcess());

		assertThat(second.getStatus()).isEqualTo(ReservationStatus.STARTED);
	}

	@Test
	void anotherUserCanHaveItsOwnActiveProcess() {
		adapter.create(startedProcess());
		ReservationProcess otherUsers = startedProcess();
		otherUsers.setUserId(insertUser("ana"));

		assertThat(adapter.create(otherUsers).getStatus()).isEqualTo(ReservationStatus.STARTED);
	}

	@Test
	void existsActiveCountsOnlyProcessesThatAreNotFinal() {
		assertThat(adapter.existsActiveByUserId(userId)).isFalse();

		adapter.create(finishedProcess());
		assertThat(adapter.existsActiveByUserId(userId)).isFalse();

		ReservationProcess active = startedProcess();
		active.setStatus(ReservationStatus.PHONE_SUBMITTED);
		adapter.create(active);
		assertThat(adapter.existsActiveByUserId(userId)).isTrue();
	}

	@Test
	void databaseRejectsAProcessOfAnUnknownUser() {
		ReservationProcess process = startedProcess();
		process.setUserId(userId + 1000);

		assertThatThrownBy(() -> adapter.create(process)).isInstanceOf(DataIntegrityViolationException.class);
	}

	private ReservationProcess read(UUID id) {
		return mapper.toDomainModel(entityManager.find(ReservationProcessEntity.class, id));
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

	private ReservationProcess finishedProcess() {
		ReservationProcess process = startedProcess();
		process.setStatus(ReservationStatus.FAILED);
		return process;
	}

	// Con todos los campos completos, aunque en la realidad un proceso no los tenga todos a la vez:
	// lo que se prueba es que cada columna se guarda y se lee.
	private ReservationProcess fullProcess() {
		ReservationProcess process = startedProcess();
		process.setStatus(ReservationStatus.CANCELLED);
		process.setHoldId("hold-6c0c2f41");
		process.setReservationProcessId("process-a92b47d0");
		process.setReservationId(8251L);
		process.setExpiresAt(Instant.parse("2026-11-10T14:44:50Z"));
		process.setRequestEventId("event-request");
		process.setSubmittedEventId("event-submitted");
		process.setLastMessage("Ingrese un numero de telefono para continuar.");
		process.setFailureReason("SLOT_ALREADY_HELD");
		process.setCancellationReason("No puedo asistir");
		process.setCancelledBy("juan");
		process.setConfirmedAt(Instant.parse("2026-11-10T14:36:00Z"));
		process.setCancelledAt(Instant.parse("2026-11-11T15:10:00Z"));
		return process;
	}

}
