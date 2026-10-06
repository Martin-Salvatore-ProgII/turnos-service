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

/**
 * Verifica que las migraciones de Flyway dejan el esquema esperado en PostgreSQL.
 */
@Import({ TestcontainersConfiguration.class, TestJwtKeysConfiguration.class })
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
