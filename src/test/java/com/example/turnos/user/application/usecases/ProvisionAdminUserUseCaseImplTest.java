package com.example.turnos.user.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.example.turnos.user.domain.model.User;
import com.example.turnos.user.domain.ports.out.PasswordHasherPort;
import com.example.turnos.user.domain.ports.out.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProvisionAdminUserUseCaseImplTest {

	@Mock
	private UserRepository repository;

	@Mock
	private PasswordHasherPort passwordHasher;

	@InjectMocks
	private ProvisionAdminUserUseCaseImpl useCase;

	@Test
	void createsTheAdminWhenTheLoginDoesNotExist() {
		when(repository.findAllByAuthority("ROLE_ADMIN")).thenReturn(List.of());
		when(repository.findByLogin("admin")).thenReturn(Optional.empty());
		when(repository.existsByEmail("admin@example.com")).thenReturn(false);
		when(passwordHasher.hash("admin-secret")).thenReturn("hashed");
		when(repository.create(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

		Optional<User> admin = useCase.provisionAdminUser(configuredAdmin("  Admin ", "Admin@Example.com"), "admin-secret");

		assertThat(admin).isPresent();
		ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
		verify(repository).create(captor.capture());
		User created = captor.getValue();
		assertThat(created.getLogin()).isEqualTo("admin");
		assertThat(created.getEmail()).isEqualTo("admin@example.com");
		assertThat(created.getPasswordHash()).isEqualTo("hashed");
		assertThat(created.getExternalPatientId()).isNotNull();
		assertThat(created.isActivated()).isTrue();
		assertThat(created.getAuthorities()).containsExactlyInAnyOrder("ROLE_USER", "ROLE_ADMIN");
	}

	@Test
	void doesNothingWhenTheAdminAlreadyExistsWithTheConfiguredPassword() {
		User admin = stored(1L, "admin", "ROLE_USER", "ROLE_ADMIN");
		when(repository.findAllByAuthority("ROLE_ADMIN")).thenReturn(List.of(admin));
		when(repository.findByLogin("admin")).thenReturn(Optional.of(admin));
		when(passwordHasher.matches("admin-secret", "stored-hash")).thenReturn(true);

		assertThat(useCase.provisionAdminUser(configuredAdmin("admin", "admin@example.com"), "admin-secret")).contains(admin);

		verify(repository, never()).create(any());
		verify(repository, never()).update(any(), any());
	}

	@Test
	void doesNotPromoteAnAccountRegisteredByAnyoneWithTheAdminLogin() {
		when(repository.findAllByAuthority("ROLE_ADMIN")).thenReturn(List.of());
		when(repository.findByLogin("admin")).thenReturn(Optional.of(stored(7L, "admin", "ROLE_USER")));
		when(passwordHasher.matches("admin-secret", "stored-hash")).thenReturn(false);

		assertThat(useCase.provisionAdminUser(configuredAdmin("admin", "admin@example.com"), "admin-secret")).isEmpty();

		verify(repository, never()).create(any());
		verify(repository, never()).update(any(), any());
	}

	@Test
	void givesTheRoleBackToTheAccountThatHasTheConfiguredPassword() {
		when(repository.findAllByAuthority("ROLE_ADMIN")).thenReturn(List.of());
		when(repository.findByLogin("admin")).thenReturn(Optional.of(stored(1L, "admin", "ROLE_USER")));
		when(passwordHasher.matches("admin-secret", "stored-hash")).thenReturn(true);
		when(repository.update(eq(1L), any(User.class))).thenAnswer(invocation -> Optional.of(invocation.getArgument(1)));

		assertThat(useCase.provisionAdminUser(configuredAdmin("admin", "admin@example.com"), "admin-secret")).isPresent();

		assertThat(updatedUser(1L).getAuthorities()).containsExactlyInAnyOrder("ROLE_USER", "ROLE_ADMIN");
	}

	@Test
	void removesTheRoleFromAnAdminWhosePasswordNoLongerMatches() {
		User admin = stored(1L, "admin", "ROLE_USER", "ROLE_ADMIN");
		when(repository.findAllByAuthority("ROLE_ADMIN")).thenReturn(List.of(admin));
		when(repository.findByLogin("admin")).thenReturn(Optional.of(admin));
		when(passwordHasher.matches("otra-clave", "stored-hash")).thenReturn(false);

		assertThat(useCase.provisionAdminUser(configuredAdmin("admin", "admin@example.com"), "otra-clave")).isEmpty();

		assertThat(updatedUser(1L).getAuthorities()).containsExactly("ROLE_USER");
	}

	@Test
	void removesTheRoleFromAPreviousAdminWithAnotherLogin() {
		when(repository.findAllByAuthority("ROLE_ADMIN")).thenReturn(List.of(stored(2L, "viejo", "ROLE_USER", "ROLE_ADMIN")));
		when(repository.findByLogin("admin")).thenReturn(Optional.empty());
		when(repository.create(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

		useCase.provisionAdminUser(configuredAdmin("admin", "admin@example.com"), "admin-secret");

		assertThat(updatedUser(2L).getAuthorities()).containsExactly("ROLE_USER");
	}

	@Test
	void doesNotCreateTheAdminWhenTheEmailBelongsToAnotherAccount() {
		when(repository.findAllByAuthority("ROLE_ADMIN")).thenReturn(List.of());
		when(repository.findByLogin("admin")).thenReturn(Optional.empty());
		when(repository.existsByEmail("admin@example.com")).thenReturn(true);

		assertThat(useCase.provisionAdminUser(configuredAdmin("admin", "admin@example.com"), "admin-secret")).isEmpty();

		verify(repository, never()).create(any());
	}

	private User configuredAdmin(String login, String email) {
		return User.builder().login(login).email(email).firstName("Admin").lastName("Admin").langKey("es").build();
	}

	private User stored(Long id, String login, String... authorities) {
		return User.builder().id(id).login(login).passwordHash("stored-hash").authorities(Set.of(authorities)).build();
	}

	private User updatedUser(Long id) {
		ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
		verify(repository).update(eq(id), captor.capture());
		return captor.getValue();
	}

}
