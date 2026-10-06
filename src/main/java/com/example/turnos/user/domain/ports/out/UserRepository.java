package com.example.turnos.user.domain.ports.out;

import java.util.List;
import java.util.Optional;

import com.example.turnos.user.domain.model.User;

public interface UserRepository {

	User create(User user);

	Optional<User> findByLogin(String login);

	List<User> findAllByAuthority(String authority);

	Optional<User> update(Long id, User user);

	boolean existsByLogin(String login);

	boolean existsByEmail(String email);

}
