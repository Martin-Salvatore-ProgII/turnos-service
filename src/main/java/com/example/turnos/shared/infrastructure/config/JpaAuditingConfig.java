package com.example.turnos.shared.infrastructure.config;

import java.util.Optional;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Activa la auditoría de Spring Data: al guardar una entity, completa sola quién y cuándo la creó
 * y la modificó. Es el mismo mecanismo que usa JHipster para su modelo de usuario.
 */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {

	private static final String SYSTEM = "system";

	// "Quién" hizo el cambio: el login del usuario autenticado. Cuando no hay ninguno, como en
	// el registro (quien se registra todavía no tiene cuenta), es el sistema (ADR-0058).
	@Bean
	public AuditorAware<String> auditorAware() {
		return () -> Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication())
				.filter(Authentication::isAuthenticated)
				.filter(authentication -> !(authentication instanceof AnonymousAuthenticationToken))
				.map(Authentication::getName)
				.or(() -> Optional.of(SYSTEM));
	}

}
