package com.example.turnos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

/**
 * Verifica que las migraciones de Flyway dejan el esquema esperado en PostgreSQL.
 */
@Import({ TestcontainersConfiguration.class, TestJwtKeysConfiguration.class, TestCatedraConfiguration.class, TestCatalogConfiguration.class })
@SpringBootTest
class DatabaseMigrationTest {

	@Autowired
	private JdbcClient jdbcClient;

	@Test
	void createsUserAndAuthorityTables() {
		List<String> tables = jdbcClient
				.sql("SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'")
				.query(String.class)
				.list();

		assertThat(tables).contains("app_user", "authority", "app_user_authority");
	}

	@Test
	void createsReservationProcessTable() {
		List<String> tables = jdbcClient
				.sql("SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'")
				.query(String.class)
				.list();

		assertThat(tables).contains("reservation_process");
	}

	// Transaccional para que el usuario insertado se deshaga y no choque con otras pruebas.
	@Test
	@Transactional
	void rejectsAnUnknownReservationStatus() {
		insertUser("juan", "juan@example.com");

		assertThatThrownBy(() -> jdbcClient.sql("""
				INSERT INTO reservation_process (id, user_id, status, professional_id, professional_first_name,
						professional_last_name, appointment_date, start_time, end_time, created_at, updated_at, version)
				SELECT gen_random_uuid(), id, 'PENDING', 15, 'Ana', 'Gomez', DATE '2026-11-16', TIME '10:00',
						TIME '10:30', now(), now(), 0
				FROM app_user WHERE login = 'juan'
				""").update())
				.isInstanceOf(DataIntegrityViolationException.class)
				.hasMessageContaining("ck_reservation_process_status");
	}

	@Test
	void seedsUserAndAdminAuthorities() {
		List<String> authorities = jdbcClient.sql("SELECT name FROM authority").query(String.class).list();

		assertThat(authorities).containsExactlyInAnyOrder("ROLE_USER", "ROLE_ADMIN");
	}

	@Test
	void rejectsLoginWithUppercase() {
		assertThatThrownBy(() -> insertUser("Juan", "juan@example.com"))
				.isInstanceOf(DataIntegrityViolationException.class)
				.hasMessageContaining("ck_app_user_login_lowercase");
	}

	@Test
	void rejectsEmailWithUppercase() {
		assertThatThrownBy(() -> insertUser("juan", "Juan@example.com"))
				.isInstanceOf(DataIntegrityViolationException.class)
				.hasMessageContaining("ck_app_user_email_lowercase");
	}

	private void insertUser(String login, String email) {
		jdbcClient.sql("""
				INSERT INTO app_user (external_patient_id, login, password_hash, first_name, last_name, email,
						activated, lang_key, created_by, created_date)
				VALUES (gen_random_uuid(), :login, 'hash', 'Juan', 'Perez', :email, true, 'es', 'test', now())
				""")
				.param("login", login)
				.param("email", email)
				.update();
	}

}
