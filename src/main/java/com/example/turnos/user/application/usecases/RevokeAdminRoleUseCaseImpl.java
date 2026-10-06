package com.example.turnos.user.application.usecases;

import java.util.HashSet;
import java.util.Set;

import com.example.turnos.user.domain.ports.in.RevokeAdminRoleUseCase;
import com.example.turnos.user.domain.ports.out.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RevokeAdminRoleUseCaseImpl implements RevokeAdminRoleUseCase {

	private static final String ROLE_ADMIN = "ROLE_ADMIN";

	private final UserRepository repository;

	// "Si no se indica, no hay administrador" (ADR-0037): la cuenta sigue existiendo como
	// usuario común, pero pierde el rol.
	@Override
	public void revokeAdminRole() {
		repository.findAllByAuthority(ROLE_ADMIN).forEach(user -> {
			Set<String> authorities = new HashSet<>(user.getAuthorities());
			authorities.remove(ROLE_ADMIN);
			user.setAuthorities(authorities);
			repository.update(user.getId(), user);
		});
	}

}
