package com.example.turnos.user.infrastructure.web.mapper;

import com.example.turnos.user.domain.model.User;
import com.example.turnos.user.infrastructure.web.dto.UserRequest;
import org.springframework.stereotype.Component;

@Component
public class UserDtoMapper {

	// La contraseña no se copia al modelo: el dominio solo conoce su hash. El controller la pasa aparte.
	public User toDomain(UserRequest request) {
		if (request == null) {
			return null;
		}
		return User.builder()
				.login(request.getLogin())
				.firstName(request.getFirstName())
				.lastName(request.getLastName())
				.email(request.getEmail())
				.imageUrl(request.getImageUrl())
				.langKey(request.getLangKey())
				.build();
	}

}
