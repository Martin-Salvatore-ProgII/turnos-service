package com.example.turnos.reservation.infrastructure.persistence.entity;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import com.example.turnos.reservation.domain.model.ReservationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Getter
@Setter
@Entity
@Table(name = "reservation_process")
@EntityListeners(AuditingEntityListener.class)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReservationProcessEntity {
	// El id lo asigna el servicio al crear el proceso, no la base: es el que ve la app.
	@Id
	private UUID id;
	private Long userId;
	@Enumerated(EnumType.STRING)
	private ReservationStatus status;
	private Long professionalId;
	private String professionalFirstName;
	private String professionalLastName;
	private LocalDate appointmentDate;
	private LocalTime startTime;
	private LocalTime endTime;
	private String holdId;
	private String reservationProcessId;
	private Long reservationId;
	private Instant expiresAt;
	private String requestEventId;
	private String submittedEventId;
	private String lastMessage;
	private String failureReason;
	private String cancellationReason;
	private String cancelledBy;
	// Las fechas de creación y de último cambio las completa Spring Data al guardar (ADR-0058).
	@CreatedDate
	@Column(updatable = false)
	private Instant createdAt;
	@LastModifiedDate
	private Instant updatedAt;
	private Instant confirmedAt;
	private Instant cancelledAt;
	// Bloqueo optimista (ADR-0068): Hibernate guarda con "WHERE version = <la leída>" y la aumenta.
	// Si otro cambio la aumentó antes, no guarda nada y lanza una excepción. Que sea null indica
	// además que el proceso es nuevo.
	@Version
	private Long version;
}
