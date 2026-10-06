package com.example.turnos.user.domain.ports.out;

import com.example.turnos.user.domain.model.User;

public interface UserRepository {

	User create(User user);

	boolean existsByLogin(String login);

	boolean existsByEmail(String email);

}
