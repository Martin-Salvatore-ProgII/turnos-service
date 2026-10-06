package com.example.turnos.user.infrastructure.persistence.adapter;

import com.example.turnos.user.domain.model.User;
import com.example.turnos.user.domain.ports.out.UserRepository;
import com.example.turnos.user.infrastructure.persistence.entity.UserEntity;
import com.example.turnos.user.infrastructure.persistence.mapper.UserMapper;
import com.example.turnos.user.infrastructure.persistence.repository.JpaUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JpaUserRepositoryAdapter implements UserRepository {

	private final JpaUserRepository jpaUserRepository;
	private final UserMapper userMapper;

	@Override
	public User create(User user) {
		UserEntity savedEntity = jpaUserRepository.save(userMapper.toEntity(user));
		return userMapper.toDomainModel(savedEntity);
	}

	@Override
	public boolean existsByLogin(String login) {
		return jpaUserRepository.existsByLogin(login);
	}

	@Override
	public boolean existsByEmail(String email) {
		return jpaUserRepository.existsByEmail(email);
	}

}
