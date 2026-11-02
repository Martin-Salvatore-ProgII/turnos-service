package com.example.turnos.user.domain.ports.out;

import java.util.List;
import java.util.Optional;

import com.example.turnos.user.domain.model.User;

public interface UserRepository {

	User create(User user);

	Optional<User> findByLogin(String login);

	// Por su id: lo usa la reconciliación, que parte del dueño de un proceso y no de un pedido con JWT.
	Optional<User> findById(Long id);

	List<User> findAllByAuthority(String authority);

	Optional<User> update(Long id, User user);

	boolean existsByLogin(String login);

	boolean existsByEmail(String email);

}
