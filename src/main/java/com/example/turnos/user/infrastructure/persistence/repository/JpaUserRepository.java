package com.example.turnos.user.infrastructure.persistence.repository;

import com.example.turnos.user.infrastructure.persistence.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface JpaUserRepository extends JpaRepository<UserEntity, Long> {

	boolean existsByLogin(String login);

	boolean existsByEmail(String email);

}
