package com.example.turnos.user.domain.ports.in;

import com.example.turnos.user.domain.model.User;

public interface RegisterUserUseCase {

	// La contraseña viaja aparte porque no es un dato del usuario: el modelo solo guarda su hash.
	User registerUser(User user, String rawPassword);

}
