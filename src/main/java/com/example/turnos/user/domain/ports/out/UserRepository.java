package com.example.turnos.user.domain.ports.out;

import java.util.Optional;

import com.example.turnos.user.domain.model.User;

public interface UserRepository {

	User create(User user);

	Optional<User> findByLogin(String login);

	boolean existsByLogin(String login);

	boolean existsByEmail(String email);

}
