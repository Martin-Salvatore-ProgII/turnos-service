package com.example.turnos.user.infrastructure.startup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import com.example.turnos.user.application.service.UserService;
import com.example.turnos.user.domain.model.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminUserInitializerTest {

	@Mock
	private UserService userService;

	@Test
	void provisionsTheAdminWithTheConfiguredCredentials() {
		when(userService.provisionAdminUser(any(User.class), eq("admin-secret")))
				.thenReturn(Optional.of(User.builder().login("admin").build()));

		new AdminUserInitializer(userService, "admin", "admin-secret", "admin@example.com").run(null);

		ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
		verify(userService).provisionAdminUser(captor.capture(), eq("admin-secret"));
		assertThat(captor.getValue().getLogin()).isEqualTo("admin");
		assertThat(captor.getValue().getEmail()).isEqualTo("admin@example.com");
		verify(userService, never()).revokeAdminRole();
	}

	@Test
	void withoutConfigurationLeavesNoAdmin() {
		new AdminUserInitializer(userService, "", "", "").run(null);

		verify(userService).revokeAdminRole();
		verify(userService, never()).provisionAdminUser(any(), any());
	}

	@Test
	void startsNormallyWhenTheAdminCannotBeProvisioned() {
		when(userService.provisionAdminUser(any(User.class), any())).thenReturn(Optional.empty());

		assertThatCode(() -> new AdminUserInitializer(userService, "admin", "admin-secret", "admin@example.com").run(null))
				.doesNotThrowAnyException();
	}

	@ParameterizedTest
	@CsvSource({ "admin,'',admin@example.com", "admin,admin-secret,''", "'',admin-secret,admin@example.com", "admin,'',''" })
	void incompleteConfigurationStopsTheStartup(String login, String password, String email) {
		assertThatThrownBy(() -> new AdminUserInitializer(userService, login, password, email).run(null))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("Incomplete admin configuration");

		verify(userService, never()).provisionAdminUser(any(), any());
		verify(userService, never()).revokeAdminRole();
	}

}
