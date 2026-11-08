package com.example.turnos.reservation.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.turnos.reservation.domain.ports.out.ProcessedEventRepository;

@ExtendWith(MockitoExtension.class)
class PurgeProcessedEventsUseCaseImplTest {

	private static final Instant NOW = Instant.parse("2026-11-02T15:00:00Z");

	@Mock
	private ProcessedEventRepository processedEventRepository;

	private PurgeProcessedEventsUseCaseImpl useCase;

	@BeforeEach
	void setUp() {
		useCase = new PurgeProcessedEventsUseCaseImpl(processedEventRepository, Clock.fixed(NOW, ZoneOffset.UTC));
	}

	@Test
	void deletesTheEventsOlderThanTheRetentionAndSaysHowMany() {
		when(processedEventRepository.deleteProcessedBefore(NOW.minus(Duration.ofDays(7)))).thenReturn(3);

		assertThat(useCase.purgeProcessedEvents(Duration.ofDays(7))).isEqualTo(3);

		verify(processedEventRepository).deleteProcessedBefore(Instant.parse("2026-10-26T15:00:00Z"));
	}

}
