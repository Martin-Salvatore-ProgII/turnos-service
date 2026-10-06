package com.example.turnos.user.domain.ports.in;

public interface RevokeAdminRoleUseCase {

	// Le quita el rol de administrador a todos los usuarios que lo tengan.
	void revokeAdminRole();

}
