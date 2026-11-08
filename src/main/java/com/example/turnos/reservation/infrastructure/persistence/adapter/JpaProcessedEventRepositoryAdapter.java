package com.example.turnos.reservation.infrastructure.persistence.adapter;

import java.time.Instant;

import org.springframework.stereotype.Component;

import com.example.turnos.reservation.domain.ports.out.ProcessedEventRepository;
import com.example.turnos.reservation.infrastructure.persistence.repository.JpaProcessedEventRepository;

import lombok.RequiredArgsConstructor;

// No tiene mapper: no hay modelo de dominio de un evento procesado. Al dominio solo le importan el
// identificador y si era nuevo.
@Component
@RequiredArgsConstructor
public class JpaProcessedEventRepositoryAdapter implements ProcessedEventRepository {

	private final JpaProcessedEventRepository jpaProcessedEventRepository;

	@Override
	public boolean registerIfNew(String eventId, Instant processedAt) {
		return jpaProcessedEventRepository.insertIfAbsent(eventId, processedAt) == 1;
	}

	@Override
	public int deleteProcessedBefore(Instant cutoff) {
		return jpaProcessedEventRepository.deleteProcessedBefore(cutoff);
	}

}
