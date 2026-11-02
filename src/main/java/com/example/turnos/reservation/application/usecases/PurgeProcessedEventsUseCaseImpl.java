package com.example.turnos.reservation.application.usecases;

import java.time.Clock;
import java.time.Duration;

import org.springframework.stereotype.Component;

import com.example.turnos.reservation.domain.ports.in.PurgeProcessedEventsUseCase;
import com.example.turnos.reservation.domain.ports.out.ProcessedEventRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

// Sin purga, la tabla crecería con cada evento para siempre. Un evento viejo que se repitiera después
// de purgado no hace daño: la máquina de estados lo ignora (ADR-0008, ADR-0023).
@Slf4j
@Component
@RequiredArgsConstructor
public class PurgeProcessedEventsUseCaseImpl implements PurgeProcessedEventsUseCase {

	private final ProcessedEventRepository processedEventRepository;
	private final Clock clock;

	@Override
	public int purgeProcessedEvents(Duration retention) {
		int deleted = processedEventRepository.deleteProcessedBefore(clock.instant().minus(retention));
		if (deleted > 0) {
			log.info("Purged {} processed reservation events older than {}", deleted, retention);
		}
		return deleted;
	}

}
