package com.example.turnos.user.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;
import java.util.UUID;

import com.example.turnos.TestcontainersConfiguration;
import com.example.turnos.shared.infrastructure.config.JpaAuditingConfig;
import com.example.turnos.user.domain.model.User;
import com.example.turnos.user.infrastructure.persistence.mapper.UserMapper;
import com.example.turnos.user.infrastructure.persistence.repository.JpaUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

// Prueba el adaptador contra PostgreSQL real, con las migraciones de Flyway (ADR-0045).
// Cada test corre en una transacción que se deshace al terminar.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ TestcontainersConfiguration.class, JpaAuditingConfig.class, JpaUserRepositoryAdapter.class, UserMapper.class })
class JpaUserRepositoryAdapterTest {

	@Autowired
	private JpaUserRepositoryAdapter adapter;

	// Solo para forzar el envío del SQL pendiente a la base dentro de la transacción del test.
	@Autowired
	private JpaUserRepository jpaUserRepository;

	@Autowired
	private TestEntityManager entityManager;

	@Test
	void createPersistsTheUserAndReturnsItWithId() {
		User user = newUser("juan", "juan@example.com");

		User created = adapter.create(user);
		jpaUserRepository.flush();

		assertThat(created.getId()).isNotNull();
		assertThat(created.getExternalPatientId()).isEqualTo(user.getExternalPatientId());
		assertThat(created.getLogin()).isEqualTo("juan");
		assertThat(created.getPasswordHash()).isEqualTo("hashed");
		assertThat(created.getEmail()).isEqualTo("juan@example.com");
		assertThat(created.isActivated()).isTrue();
		assertThat(created.getAuthorities()).containsExactly("ROLE_USER");
	}

	@Test
	void createFillsTheAuditFields() {
		User created = adapter.create(newUser("juan", "juan@example.com"));

		assertThat(created.getCreatedBy()).isEqualTo("system");
		assertThat(created.getCreatedDate()).isNotNull();
		assertThat(created.getLastModifiedBy()).isEqualTo("system");
		assertThat(created.getLastModifiedDate()).isNotNull();
	}

	@Test
	void findByLoginReadsTheUserBackFromTheDatabase() {
		User user = newUser("juan", "juan@example.com");
		adapter.create(user);
		// Se vacía la memoria de JPA para que la búsqueda lea de verdad de PostgreSQL.
		entityManager.flush();
		entityManager.clear();

		User found = adapter.findByLogin("juan").orElseThrow();

		assertThat(found.getExternalPatientId()).isEqualTo(user.getExternalPatientId());
		assertThat(found.getPasswordHash()).isEqualTo("hashed");
		assertThat(found.getFirstName()).isEqualTo("Juan");
		assertThat(found.getLastName()).isEqualTo("Perez");
		assertThat(found.getEmail()).isEqualTo("juan@example.com");
		assertThat(found.getLangKey()).isEqualTo("es");
		assertThat(found.isActivated()).isTrue();
		assertThat(found.getAuthorities()).containsExactly("ROLE_USER");
		assertThat(found.getCreatedBy()).isEqualTo("system");
	}

	@Test
	void findByLoginReturnsEmptyForUnknownLogin() {
		assertThat(adapter.findByLogin("nadie")).isEmpty();
	}

	@Test
	void updateChangesTheAuthoritiesAndKeepsTheRestOfTheUser() {
		User created = adapter.create(newUser("ana", "ana@example.com"));
		entityManager.flush();
		entityManager.clear();

		created.setAuthorities(Set.of("ROLE_USER", "ROLE_ADMIN"));
		adapter.update(created.getId(), created);
		entityManager.flush();
		entityManager.clear();

		User found = adapter.findByLogin("ana").orElseThrow();
		assertThat(found.getAuthorities()).containsExactlyInAnyOrder("ROLE_USER", "ROLE_ADMIN");
		assertThat(found.getPasswordHash()).isEqualTo("hashed");
		assertThat(found.getExternalPatientId()).isEqualTo(created.getExternalPatientId());
		assertThat(found.getCreatedBy()).isEqualTo("system");
		assertThat(found.getCreatedDate()).isNotNull();
	}

	@Test
	void updateReturnsEmptyForUnknownId() {
		assertThat(adapter.update(999L, newUser("nadie", "nadie@example.com"))).isEmpty();
	}

	@Test
	void findAllByAuthorityReturnsOnlyUsersWithThatRole() {
		adapter.create(newUser("juan", "juan@example.com"));
		User ana = newUser("ana", "ana@example.com");
		ana.setAuthorities(Set.of("ROLE_USER", "ROLE_ADMIN"));
		adapter.create(ana);
		entityManager.flush();
		entityManager.clear();

		assertThat(adapter.findAllByAuthority("ROLE_ADMIN")).extracting(User::getLogin).containsExactly("ana");
		assertThat(adapter.findAllByAuthority("ROLE_USER")).extracting(User::getLogin).containsExactlyInAnyOrder("juan", "ana");
	}

	@Test
	void existsByLoginAndEmailFindOnlyStoredValues() {
		adapter.create(newUser("juan", "juan@example.com"));

		assertThat(adapter.existsByLogin("juan")).isTrue();
		assertThat(adapter.existsByLogin("maria")).isFalse();
		assertThat(adapter.existsByEmail("juan@example.com")).isTrue();
		assertThat(adapter.existsByEmail("maria@example.com")).isFalse();
	}

	@Test
	void databaseRejectsADuplicatedLogin() {
		adapter.create(newUser("juan", "juan@example.com"));

		assertThatThrownBy(() -> adapter.create(newUser("juan", "otro@example.com")))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void databaseRejectsAnAuthorityThatDoesNotExist() {
		User user = newUser("juan", "juan@example.com");
		user.setAuthorities(Set.of("ROLE_INVENTED"));

		assertThatThrownBy(() -> {
			adapter.create(user);
			jpaUserRepository.flush();
		}).isInstanceOf(DataIntegrityViolationException.class);
	}

	private User newUser(String login, String email) {
		return User.builder()
				.externalPatientId(UUID.randomUUID())
				.login(login)
				.passwordHash("hashed")
				.firstName("Juan")
				.lastName("Perez")
				.email(email)
				.activated(true)
				.langKey("es")
				.authorities(Set.of("ROLE_USER"))
				.build();
	}

}
