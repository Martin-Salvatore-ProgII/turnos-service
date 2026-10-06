package com.example.turnos.user.domain.ports.in;

import java.util.Optional;

import com.example.turnos.user.domain.model.User;

public interface ProvisionAdminUserUseCase {

	// Deja a ese usuario como único administrador, creándolo si no existe. Vacío si el login o
	// el email ya pertenecen a otra cuenta.
	Optional<User> provisionAdminUser(User adminUser, String rawPassword);

}
