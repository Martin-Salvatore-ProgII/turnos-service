package com.example.turnos.availability.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;

import com.example.turnos.availability.application.exception.AvailabilityException;
import com.example.turnos.availability.domain.model.Availability;
import com.example.turnos.availability.domain.ports.in.GetAvailabilityUseCase;
import org.junit.jupiter.api.Test;

// La fachada solo delega: los rechazos ya vienen con su código del caso de uso (ADR-0059).
class AvailabilityServiceTest {

	private static final LocalDate DATE = LocalDate.parse("2026-11-09");

	private final GetAvailabilityUseCase getAvailabilityUseCase = mock(GetAvailabilityUseCase.class);
	private final AvailabilityService service = new AvailabilityService(getAvailabilityUseCase);

	@Test
	void returnsTheAvailabilityOfTheUseCase() {
		Availability availability = Availability.builder().professionalId(204L).date(DATE).build();
		when(getAvailabilityUseCase.getAvailability(204L, DATE)).thenReturn(availability);

		assertThat(service.getAvailability(204L, DATE)).isSameAs(availability);
	}

	@Test
	void letsTheRejectionOfTheUseCasePass() {
		when(getAvailabilityUseCase.getAvailability(9999L, DATE)).thenThrow(new AvailabilityException(
				AvailabilityException.Code.PROFESSIONAL_NOT_FOUND, "Could not find professional with id: 9999"));

		assertThatThrownBy(() -> service.getAvailability(9999L, DATE))
				.isInstanceOf(AvailabilityException.class)
				.extracting("code").isEqualTo(AvailabilityException.Code.PROFESSIONAL_NOT_FOUND);
	}

}
