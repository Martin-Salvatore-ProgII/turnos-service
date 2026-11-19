package com.example.turnos.reservation.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import com.example.turnos.TestcontainersConfiguration;
import com.example.turnos.reservation.application.exception.ReservationException;
import com.example.turnos.reservation.application.exception.ReservationProcessConflictException;
import com.example.turnos.reservation.domain.model.PageQuery;
import com.example.turnos.reservation.domain.model.PageResult;
import com.example.turnos.reservation.domain.model.ReservationProcess;
import com.example.turnos.reservation.domain.model.ReservationSearchCriteria;
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
	void findByIdAndUserIdReturnsTheProcessOnlyToItsOwner() {
		// La reserva de otro usuario no sale de la base: la consulta ya filtra por el dueño.
		ReservationProcess created = adapter.create(startedProcess());
		Long otherUser = insertUser("ana");
		entityManager.flush();
		entityManager.clear();

		assertThat(adapter.findByIdAndUserId(created.getId(), userId)).isPresent();
		assertThat(adapter.findByIdAndUserId(created.getId(), otherUser)).isEmpty();
		assertThat(adapter.findByIdAndUserId(UUID.randomUUID(), userId)).isEmpty();
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

	@Test
	void findsAProcessByTheReservationProcessIdOfTheCatedra() {
		ReservationProcess process = adapter.create(fullProcess());

		assertThat(adapter.findByReservationProcessId(process.getReservationProcessId()))
				.hasValueSatisfying(found -> assertThat(found.getId()).isEqualTo(process.getId()));
		assertThat(adapter.findByReservationProcessId("process-que-no-existe")).isEmpty();
	}

	@Test
	void findsAProcessByItsLocalIdWithoutFilteringByOwner() {
		ReservationProcess process = adapter.create(startedProcess());

		assertThat(adapter.findById(process.getId())).hasValueSatisfying(found -> assertThat(found.getUserId())
				.isEqualTo(process.getUserId()));
		assertThat(adapter.findById(UUID.randomUUID())).isEmpty();
	}

	// Las consultas de la reconciliación (CU-05, ADR-0030).
	@Test
	void findsTheStartedProcessesCreatedBeforeTheCutoff() {
		ReservationProcess started = adapter.create(startedProcess());
		entityManager.flush();

		assertThat(adapter.findStartedCreatedBefore(Instant.now().plusSeconds(60))).extracting(ReservationProcess::getId)
				.contains(started.getId());
		assertThat(adapter.findStartedCreatedBefore(Instant.now().minusSeconds(3600))).extracting(ReservationProcess::getId)
				.doesNotContain(started.getId());
	}

	@Test
	void findsOnlyTheProcessesInTheGivenStatusesWhoseExpirationPassed() {
		Instant expiresAt = Instant.parse("2026-11-02T15:00:00Z");
		ReservationProcess held = adapter.create(withStatus(ReservationStatus.HELD, expiresAt));
		// Solo un proceso activo por usuario (ADR-0024): cada uno de otro dueño.
		userId = insertUser("ana");
		ReservationProcess submitted = adapter.create(withStatus(ReservationStatus.PHONE_SUBMITTED, expiresAt));
		userId = insertUser("luis");
		ReservationProcess notExpiredYet = adapter
				.create(withStatus(ReservationStatus.AWAITING_PHONE, expiresAt.plusSeconds(600)));
		entityManager.flush();

		List<ReservationProcess> found = adapter.findByStatusInAndExpiresAtBefore(
				EnumSet.of(ReservationStatus.HELD, ReservationStatus.AWAITING_PHONE), expiresAt.plusSeconds(1));

		assertThat(found).extracting(ReservationProcess::getId).contains(held.getId())
				.doesNotContain(submitted.getId(), notExpiredYet.getId());
	}

	@Test
	void findsTheConfirmedReservationsFromTheDateOn() {
		ReservationProcess future = withStatus(ReservationStatus.CONFIRMED, null);
		future.setAppointmentDate(LocalDate.of(2026, 11, 20));
		future = adapter.create(future);
		userId = insertUser("ana");
		ReservationProcess past = withStatus(ReservationStatus.CONFIRMED, null);
		past.setAppointmentDate(LocalDate.of(2026, 10, 1));
		past = adapter.create(past);
		entityManager.flush();

		assertThat(adapter.findConfirmedFrom(LocalDate.of(2026, 11, 2))).extracting(ReservationProcess::getId)
				.contains(future.getId()).doesNotContain(past.getId());
	}

	// Listar las reservas propias (HU-08, ADR-0025).
	@Test
	void findByUserIdReturnsOnlyTheProcessesOfTheUserFromTheNewestOn() {
		ReservationProcess oldest = createdAt(adapter.create(finishedProcess()), "2026-11-01T10:00:00Z");
		ReservationProcess newest = createdAt(adapter.create(finishedProcess()), "2026-11-03T10:00:00Z");
		ReservationProcess middle = createdAt(adapter.create(finishedProcess()), "2026-11-02T10:00:00Z");
		userId = insertUser("ana");
		ReservationProcess otherUsers = adapter.create(finishedProcess());
		entityManager.flush();
		entityManager.clear();

		PageResult<ReservationProcess> found = adapter.findByUserId(oldest.getUserId(), null, page(0, 20));

		// La reserva de otro usuario no sale de la base: la consulta ya filtra por el dueño.
		assertThat(found.getContent()).extracting(ReservationProcess::getId)
				.containsExactly(newest.getId(), middle.getId(), oldest.getId())
				.doesNotContain(otherUsers.getId());
		assertThat(found.getTotalElements()).isEqualTo(3);
	}

	@Test
	void findByUserIdCutsThePageAndCountsAllTheProcesses() {
		ReservationProcess oldest = createdAt(adapter.create(finishedProcess()), "2026-11-01T10:00:00Z");
		createdAt(adapter.create(finishedProcess()), "2026-11-02T10:00:00Z");
		createdAt(adapter.create(finishedProcess()), "2026-11-03T10:00:00Z");
		entityManager.flush();
		entityManager.clear();

		PageResult<ReservationProcess> found = adapter.findByUserId(userId, null, page(1, 2));

		assertThat(found.getContent()).extracting(ReservationProcess::getId).containsExactly(oldest.getId());
		assertThat(found.getTotalElements()).isEqualTo(3);
		assertThat(found.getPage()).isEqualTo(1);
		assertThat(found.getSize()).isEqualTo(2);
	}

	@Test
	void findByUserIdFiltersByStatus() {
		ReservationProcess confirmed = adapter.create(withStatus(ReservationStatus.CONFIRMED, null));
		adapter.create(finishedProcess());
		adapter.create(startedProcess());
		entityManager.flush();
		entityManager.clear();

		PageResult<ReservationProcess> found = adapter.findByUserId(userId, ReservationStatus.CONFIRMED, page(0, 20));

		assertThat(found.getContent()).extracting(ReservationProcess::getId).containsExactly(confirmed.getId());
		assertThat(found.getTotalElements()).isEqualTo(1);
	}

	@Test
	void findByUserIdOfAUserWithoutProcessesIsAnEmptyPage() {
		PageResult<ReservationProcess> found = adapter.findByUserId(userId, null, page(0, 20));

		assertThat(found.getContent()).isEmpty();
		assertThat(found.getTotalElements()).isZero();
	}

	@Test
	void searchWithoutFiltersReturnsTheProcessesOfEveryUserFromTheNewestOn() {
		ReservationProcess juans = createdAt(adapter.create(finishedProcess()), "2026-11-01T10:00:00Z");
		userId = insertUser("ana");
		ReservationProcess anas = createdAt(adapter.create(finishedProcess()), "2026-11-02T10:00:00Z");
		entityManager.flush();
		entityManager.clear();

		PageResult<ReservationProcess> found = adapter.search(ReservationSearchCriteria.builder().build(), page(0, 20));

		assertThat(found.getContent()).extracting(ReservationProcess::getId).containsExactly(anas.getId(), juans.getId());
		assertThat(found.getTotalElements()).isEqualTo(2);
	}

	@Test
	void searchFiltersByOwner() {
		ReservationProcess juans = adapter.create(finishedProcess());
		Long juanId = userId;
		userId = insertUser("ana");
		adapter.create(finishedProcess());
		entityManager.flush();
		entityManager.clear();

		PageResult<ReservationProcess> found =
				adapter.search(ReservationSearchCriteria.builder().ownerId(juanId).build(), page(0, 20));

		assertThat(found.getContent()).extracting(ReservationProcess::getId).containsExactly(juans.getId());
	}

	@Test
	void searchFiltersByStatus() {
		ReservationProcess confirmed = adapter.create(withStatus(ReservationStatus.CONFIRMED, null));
		adapter.create(finishedProcess());
		entityManager.flush();
		entityManager.clear();

		PageResult<ReservationProcess> found = adapter.search(
				ReservationSearchCriteria.builder().status(ReservationStatus.CONFIRMED).build(), page(0, 20));

		assertThat(found.getContent()).extracting(ReservationProcess::getId).containsExactly(confirmed.getId());
	}

	// Los dos extremos del rango van incluidos, y puede venir uno solo.
	@Test
	void searchFiltersByTheDateOfTheAppointmentIncludingBothEnds() {
		ReservationProcess before = adapter.create(onDate("2026-11-09"));
		ReservationProcess first = adapter.create(onDate("2026-11-10"));
		ReservationProcess last = adapter.create(onDate("2026-11-20"));
		ReservationProcess after = adapter.create(onDate("2026-11-21"));
		entityManager.flush();
		entityManager.clear();

		LocalDate from = LocalDate.parse("2026-11-10");
		LocalDate to = LocalDate.parse("2026-11-20");
		assertThat(adapter.search(ReservationSearchCriteria.builder().from(from).to(to).build(), page(0, 20)).getContent())
				.extracting(ReservationProcess::getId).containsExactlyInAnyOrder(first.getId(), last.getId());
		assertThat(adapter.search(ReservationSearchCriteria.builder().from(from).build(), page(0, 20)).getContent())
				.extracting(ReservationProcess::getId).containsExactlyInAnyOrder(first.getId(), last.getId(), after.getId());
		assertThat(adapter.search(ReservationSearchCriteria.builder().to(to).build(), page(0, 20)).getContent())
				.extracting(ReservationProcess::getId).containsExactlyInAnyOrder(before.getId(), first.getId(), last.getId());
	}

	@Test
	void searchCombinesTheFiltersAndCountsAllThatMatch() {
		ReservationProcess match1 = createdAt(adapter.create(confirmedOn("2026-11-10")), "2026-11-01T10:00:00Z");
		ReservationProcess match2 = createdAt(adapter.create(confirmedOn("2026-11-12")), "2026-11-02T10:00:00Z");
		adapter.create(onDate("2026-11-10"));
		adapter.create(confirmedOn("2026-12-01"));
		Long juanId = userId;
		userId = insertUser("ana");
		adapter.create(confirmedOn("2026-11-10"));
		entityManager.flush();
		entityManager.clear();

		ReservationSearchCriteria criteria = ReservationSearchCriteria.builder()
				.ownerId(juanId)
				.status(ReservationStatus.CONFIRMED)
				.from(LocalDate.parse("2026-11-01"))
				.to(LocalDate.parse("2026-11-30"))
				.build();
		PageResult<ReservationProcess> firstPage = adapter.search(criteria, page(0, 1));
		PageResult<ReservationProcess> secondPage = adapter.search(criteria, page(1, 1));

		assertThat(firstPage.getContent()).extracting(ReservationProcess::getId).containsExactly(match2.getId());
		assertThat(secondPage.getContent()).extracting(ReservationProcess::getId).containsExactly(match1.getId());
		assertThat(firstPage.getTotalElements()).isEqualTo(2);
	}

	private ReservationProcess onDate(String date) {
		ReservationProcess process = finishedProcess();
		process.setAppointmentDate(LocalDate.parse(date));
		return process;
	}

	private ReservationProcess confirmedOn(String date) {
		ReservationProcess process = onDate(date);
		process.setStatus(ReservationStatus.CONFIRMED);
		return process;
	}

	// La fecha de creación la pone la auditoría con la hora actual; para probar el orden sin depender de
	// cuánto tarda cada inserción, se fija a mano.
	private ReservationProcess createdAt(ReservationProcess process, String instant) {
		entityManager.flush();
		entityManager.getEntityManager()
				.createNativeQuery("UPDATE reservation_process SET created_at = :createdAt WHERE id = :id")
				.setParameter("createdAt", Instant.parse(instant))
				.setParameter("id", process.getId())
				.executeUpdate();
		return process;
	}

	private static PageQuery page(int page, int size) {
		return PageQuery.builder().page(page).size(size).build();
	}

	private ReservationProcess withStatus(ReservationStatus status, Instant expiresAt) {
		ReservationProcess process = startedProcess();
		process.setStatus(status);
		process.setExpiresAt(expiresAt);
		return process;
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
