package com.example.turnos.user.infrastructure.persistence.entity;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

// La tabla se llama app_user porque "user" es una palabra reservada de PostgreSQL.
@Getter
@Setter
@Entity
@Table(name = "app_user")
@EntityListeners(AuditingEntityListener.class)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	private UUID externalPatientId;
	private String login;
	private String passwordHash;
	private String firstName;
	private String lastName;
	private String email;
	private String imageUrl;
	private boolean activated;
	private String langKey;

	// Los roles son solo nombres: alcanza con una colección de textos sobre la tabla intermedia,
	// sin una entity aparte. Que el nombre exista lo garantiza la clave foránea a authority.
	@ElementCollection(fetch = FetchType.EAGER)
	@CollectionTable(name = "app_user_authority", joinColumns = @JoinColumn(name = "user_id"))
	@Column(name = "authority_name")
	private Set<String> authorities;

	// Los cuatro campos de auditoría los completa Spring Data al guardar (ver JpaAuditingConfig):
	// son un dato técnico de persistencia, no una regla de negocio, por eso no los toca el caso de uso.
	@CreatedBy
	@Column(updatable = false)
	private String createdBy;
	@CreatedDate
	@Column(updatable = false)
	private Instant createdDate;
	@LastModifiedBy
	private String lastModifiedBy;
	@LastModifiedDate
	private Instant lastModifiedDate;
}
