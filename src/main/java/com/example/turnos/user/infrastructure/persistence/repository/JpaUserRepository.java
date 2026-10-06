package com.example.turnos.user.infrastructure.persistence.repository;

import java.util.Optional;

import com.example.turnos.user.infrastructure.persistence.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface JpaUserRepository extends JpaRepository<UserEntity, Long> {

	Optional<UserEntity> findByLogin(String login);

	boolean existsByLogin(String login);

	boolean existsByEmail(String email);

}
