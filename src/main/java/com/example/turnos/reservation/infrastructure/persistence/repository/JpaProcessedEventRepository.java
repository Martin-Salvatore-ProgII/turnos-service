package com.example.turnos.reservation.infrastructure.persistence.repository;

import java.time.Instant;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.example.turnos.reservation.infrastructure.persistence.entity.ProcessedEventEntity;

@Repository
public interface JpaProcessedEventRepository extends JpaRepository<ProcessedEventEntity, String> {

	// Un save() de JPA primero consulta y después inserta: dos copias del mismo evento a la vez pasarían
	// las dos la consulta. Con ON CONFLICT la decisión es de la base, en una sola sentencia (ADR-0070).
	// Devuelve 1 si insertó y 0 si el evento ya estaba. Es SQL nativo porque JPQL no tiene ON CONFLICT.
	@Transactional
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query(value = """
			INSERT INTO processed_event (event_id, processed_at) VALUES (:eventId, :processedAt)
			ON CONFLICT (event_id) DO NOTHING
			""", nativeQuery = true)
	int insertIfAbsent(@Param("eventId") String eventId, @Param("processedAt") Instant processedAt);

	@Transactional
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("DELETE FROM ProcessedEventEntity e WHERE e.processedAt < :cutoff")
	int deleteProcessedBefore(@Param("cutoff") Instant cutoff);

}
