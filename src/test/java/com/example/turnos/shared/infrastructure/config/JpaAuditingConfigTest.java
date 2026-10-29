package com.example.turnos.shared.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class JpaAuditingConfigTest {

	private final AuditorAware<String> auditorAware = new JpaAuditingConfig().auditorAware();

	@AfterEach
	void clearSecurityContext() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void auditorIsSystemWhenNobodyIsAuthenticated() {
		assertThat(auditorAware.getCurrentAuditor()).contains("system");
	}

	@Test
	void auditorIsSystemForAnAnonymousRequest() {
		SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
				"key", "anonymousUser", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));

		assertThat(auditorAware.getCurrentAuditor()).contains("system");
	}

	@Test
	void auditorIsTheLoginOfTheAuthenticatedUser() {
		SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
				"juan", null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));

		assertThat(auditorAware.getCurrentAuditor()).contains("juan");
	}

}
