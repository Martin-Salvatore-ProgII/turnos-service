package com.example.turnos.reservation.infrastructure.persistence.entity;

import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "processed_event")
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcessedEventEntity {

	@Id
	private String eventId;
	private Instant processedAt;
}
