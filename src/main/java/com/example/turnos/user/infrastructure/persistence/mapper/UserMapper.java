package com.example.turnos.user.infrastructure.persistence.mapper;

import java.util.HashSet;
import java.util.Set;

import com.example.turnos.user.domain.model.User;
import com.example.turnos.user.infrastructure.persistence.entity.UserEntity;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {

	public UserEntity toEntity(User user) {
		if (user == null) {
			return null;
		}
		return UserEntity.builder()
				.id(user.getId())
				.externalPatientId(user.getExternalPatientId())
				.login(user.getLogin())
				.passwordHash(user.getPasswordHash())
				.firstName(user.getFirstName())
				.lastName(user.getLastName())
				.email(user.getEmail())
				.imageUrl(user.getImageUrl())
				.activated(user.isActivated())
				.langKey(user.getLangKey())
				.authorities(copyOf(user.getAuthorities()))
				.createdBy(user.getCreatedBy())
				.createdDate(user.getCreatedDate())
				.lastModifiedBy(user.getLastModifiedBy())
				.lastModifiedDate(user.getLastModifiedDate())
				.build();
	}

	public User toDomainModel(UserEntity entity) {
		if (entity == null) {
			return null;
		}
		return User.builder()
				.id(entity.getId())
				.externalPatientId(entity.getExternalPatientId())
				.login(entity.getLogin())
				.passwordHash(entity.getPasswordHash())
				.firstName(entity.getFirstName())
				.lastName(entity.getLastName())
				.email(entity.getEmail())
				.imageUrl(entity.getImageUrl())
				.activated(entity.isActivated())
				.langKey(entity.getLangKey())
				.authorities(copyOf(entity.getAuthorities()))
				.createdBy(entity.getCreatedBy())
				.createdDate(entity.getCreatedDate())
				.lastModifiedBy(entity.getLastModifiedBy())
				.lastModifiedDate(entity.getLastModifiedDate())
				.build();
	}

	private Set<String> copyOf(Set<String> authorities) {
		return authorities == null ? new HashSet<>() : new HashSet<>(authorities);
	}

}
