package com.example.turnos.user.domain.model;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {
	private Long id;
	// Identificador estable que se envía a la cátedra como externalPatientId (ADR-0004).
	private UUID externalPatientId;
	private String login;
	private String passwordHash;
	private String firstName;
	private String lastName;
	private String email;
	private String imageUrl;
	private boolean activated;
	private String langKey;
	private Set<String> authorities;
	private String createdBy;
	private Instant createdDate;
	private String lastModifiedBy;
	private Instant lastModifiedDate;
}
