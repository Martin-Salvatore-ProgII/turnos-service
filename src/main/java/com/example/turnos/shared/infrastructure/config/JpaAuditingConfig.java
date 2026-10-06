package com.example.turnos.shared.infrastructure.config;

import java.util.Optional;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Activa la auditoría de Spring Data: al guardar una entity, completa sola quién y cuándo la creó
 * y la modificó. Es el mismo mecanismo que usa JHipster para su modelo de usuario.
 */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {

	private static final String SYSTEM = "system";

	// "Quién" hizo el cambio. Por ahora siempre es el sistema: quien se registra todavía no tiene
	// cuenta. Cuando haya usuarios autenticados, acá se devuelve el login del que hace el pedido.
	@Bean
	public AuditorAware<String> auditorAware() {
		return () -> Optional.of(SYSTEM);
	}

}
