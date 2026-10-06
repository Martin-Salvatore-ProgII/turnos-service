package com.example.turnos.user.infrastructure.startup;

import com.example.turnos.user.application.service.UserService;
import com.example.turnos.user.domain.model.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AdminUserInitializer implements ApplicationRunner {

	private final UserService userService;
	private final String login;
	private final String password;
	private final String email;

	// Las credenciales del administrador vienen de la configuración externa, nunca del código ni
	// del repositorio (ADR-0061). Lo que no se configura llega como texto vacío.
	public AdminUserInitializer(UserService userService,
			@Value("${turnos.security.admin.login:}") String login,
			@Value("${turnos.security.admin.password:}") String password,
			@Value("${turnos.security.admin.email:}") String email) {
		this.userService = userService;
		this.login = login;
		this.password = password;
		this.email = email;
	}

	@Override
	public void run(ApplicationArguments args) {
		if (login.isBlank() && password.isBlank() && email.isBlank()) {
			userService.revokeAdminRole();
			return;
		}
		// Una configuración a medias es un error de quien opera el servicio: mejor no arrancar
		// que arrancar con un administrador distinto del que se quiso configurar.
		if (login.isBlank() || password.isBlank() || email.isBlank()) {
			throw new IllegalStateException(
					"Incomplete admin configuration: login, password and email must be set together or all left empty");
		}

		User adminUser = User.builder()
				.login(login)
				.email(email)
				.firstName("Admin")
				.lastName("Admin")
				.langKey("es")
				.build();
		if (userService.provisionAdminUser(adminUser, password).isPresent()) {
			log.info("Admin user is ready");
		} else {
			log.error("Admin user was NOT provisioned: the configured login or email already belongs to another account. "
					+ "Choose a different admin login or email");
		}
	}

}
