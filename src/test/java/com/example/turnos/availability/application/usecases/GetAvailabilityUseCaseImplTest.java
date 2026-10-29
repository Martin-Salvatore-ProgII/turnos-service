package com.example.turnos.availability.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.example.turnos.availability.application.exception.AvailabilityException;
import com.example.turnos.availability.application.exception.IntegrationUnavailableException;
import com.example.turnos.availability.domain.model.Availability;
import com.example.turnos.availability.domain.model.AvailabilityHorizon;
import com.example.turnos.availability.domain.model.Occupancy;
import com.example.turnos.availability.domain.model.OccupancyStatus;
import com.example.turnos.availability.domain.model.Slot;
import com.example.turnos.availability.domain.ports.out.OccupancyPort;
import com.example.turnos.catalog.application.exception.CatalogUnavailableException;
import com.example.turnos.catalog.domain.model.Category;
import com.example.turnos.catalog.domain.model.ProfessionalAgenda;
import com.example.turnos.catalog.domain.model.WeeklySchedule;
import com.example.turnos.catalog.domain.ports.out.CatalogClientPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

// Las reglas de CU-03 con JUnit y Mockito, sin Spring. La agenda es la real de Laura Medina (204): lunes
// de 11:00 a 15:00 y viernes de 12:00 a 16:00, slots de 20 minutos. "Ahora" es el lunes 2026-11-02 a las
// 12:30 de Argentina (15:30 UTC). Los esperados se calcularon aparte.
class GetAvailabilityUseCaseImplTest {

	private static final Instant NOW = Instant.parse("2026-11-02T15:30:00Z");
	private static final LocalDate TODAY = LocalDate.parse("2026-11-02");
	private static final LocalDate NEXT_MONDAY = LocalDate.parse("2026-11-09");
	private static final long LAURA = 204L;

	private final CatalogClientPort catalogClientPort = mock(CatalogClientPort.class);
	private final OccupancyPort occupancyPort = mock(OccupancyPort.class);

	private GetAvailabilityUseCaseImpl useCase;

	@BeforeEach
	void setUp() {
		useCase = useCaseAt(NOW);
		when(catalogClientPort.getProfessionalAgenda(LAURA)).thenReturn(Optional.of(laura()));
		when(occupancyPort.getOccupancies(anyLong(), any())).thenReturn(List.of());
	}

	@Test
	void offersTheFreeSlotsOfTheDayWithTheProfessionalAndTheCatalogVersion() {
		when(occupancyPort.getOccupancies(LAURA, NEXT_MONDAY)).thenReturn(List.of(
				occupancy(NEXT_MONDAY, "11:20", OccupancyStatus.CONFIRMED),
				occupancy(NEXT_MONDAY, "14:00", OccupancyStatus.HELD)));

		Availability availability = useCase.getAvailability(LAURA, NEXT_MONDAY);

		assertThat(availability.getProfessionalId()).isEqualTo(LAURA);
		assertThat(availability.getProfessionalFirstName()).isEqualTo("Laura");
		assertThat(availability.getProfessionalLastName()).isEqualTo("Medina");
		assertThat(availability.getDate()).isEqualTo(NEXT_MONDAY);
		assertThat(availability.getCatalogVersion()).isEqualTo(1L);
		// CONFIRMED y HELD bloquean igual (ADR-0015).
		assertThat(starts(availability)).containsExactly(
				"11:00", "11:40", "12:00", "12:20", "12:40", "13:00", "13:20", "13:40", "14:20", "14:40");
		assertThat(availability.getSlots().get(0).getEndTime()).isEqualTo(LocalTime.parse("11:20"));
	}

	@Test
	void todayOffersOnlySlotsThatHaveNotStartedInArgentina() {
		// A las 12:30 ya empezaron los de 12:20 y antes.
		assertThat(starts(useCase.getAvailability(LAURA, TODAY)))
				.containsExactly("12:40", "13:00", "13:20", "13:40", "14:00", "14:20", "14:40");
	}

	@Test
	void usesTheDateOfArgentinaAndNotTheOneOfUtc() {
		// A la 1:00 UTC del 3 en Argentina siguen siendo las 22:00 del 2: el 2 es "hoy", no una fecha
		// pasada, y a esa hora ya no queda ningún slot.
		GetAvailabilityUseCaseImpl lateUseCase = useCaseAt(Instant.parse("2026-11-03T01:00:00Z"));

		assertThat(lateUseCase.getAvailability(LAURA, TODAY).getSlots()).isEmpty();
	}

	@Test
	void rejectsADateBeforeTodayWithoutAskingAnyone() {
		assertRejected(TODAY.minusDays(1), AvailabilityException.Code.DATE_OUT_OF_RANGE);
		verifyNoInteractions(catalogClientPort, occupancyPort);
	}

	@Test
	void acceptsTheLastDayOfTheHorizon() {
		// 2026-11-02 + 60 días = 2027-01-01.
		assertThat(useCase.getAvailability(LAURA, LocalDate.parse("2027-01-01"))).isNotNull();
	}

	@Test
	void rejectsADateAfterTheHorizonWithoutAskingAnyone() {
		assertThatThrownBy(() -> useCase.getAvailability(LAURA, LocalDate.parse("2027-01-02")))
				.isInstanceOf(AvailabilityException.class)
				.hasMessage("Date 2027-01-02 must be between 2026-11-02 and 2027-01-01");
		verifyNoInteractions(catalogClientPort, occupancyPort);
	}

	@Test
	void rejectsAMissingProfessionalWithoutAskingTheCatedra() {
		when(catalogClientPort.getProfessionalAgenda(9999L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> useCase.getAvailability(9999L, NEXT_MONDAY))
				.isInstanceOf(AvailabilityException.class)
				.extracting("code").isEqualTo(AvailabilityException.Code.PROFESSIONAL_NOT_FOUND);
		verifyNoInteractions(occupancyPort);
	}

	@Test
	void rejectsADisabledProfessionalWithoutAskingTheCatedra() {
		// Carolina Molina (206) en el catálogo real: deshabilitada y sin horarios.
		when(catalogClientPort.getProfessionalAgenda(206L)).thenReturn(Optional.of(ProfessionalAgenda.builder()
				.id(206L).firstName("Carolina").lastName("Molina").enabled(false)
				.weeklySchedules(List.of()).catalogVersion(1L).build()));

		assertThatThrownBy(() -> useCase.getAvailability(206L, NEXT_MONDAY))
				.isInstanceOf(AvailabilityException.class)
				.extracting("code").isEqualTo(AvailabilityException.Code.PROFESSIONAL_DISABLED);
		verifyNoInteractions(occupancyPort);
	}

	@Test
	void givesAnEmptyListWhenTheProfessionalDoesNotWorkThatDay() {
		LocalDate tuesday = LocalDate.parse("2026-11-10");

		assertThat(useCase.getAvailability(LAURA, tuesday).getSlots()).isEmpty();
	}

	@Test
	void givesAnEmptyListWhenEverySlotIsTaken() {
		List<Occupancy> all = new ArrayList<>();
		for (String start : List.of("11:00", "11:20", "11:40", "12:00", "12:20", "12:40", "13:00", "13:20",
				"13:40", "14:00", "14:20", "14:40")) {
			all.add(occupancy(NEXT_MONDAY, start, OccupancyStatus.CONFIRMED));
		}
		when(occupancyPort.getOccupancies(LAURA, NEXT_MONDAY)).thenReturn(all);

		assertThat(useCase.getAvailability(LAURA, NEXT_MONDAY).getSlots()).isEmpty();
	}

	@Test
	void anOccupancyOnlyBlocksTheSlotThatStartsAtItsTime() {
		// Una ocupación a las 11:30 (por ejemplo, de una agenda vieja de 30 minutos) no coincide con ningún
		// slot de 20: no bloquea nada, y la cátedra decide al crear el hold.
		when(occupancyPort.getOccupancies(LAURA, NEXT_MONDAY))
				.thenReturn(List.of(occupancy(NEXT_MONDAY, "11:30", OccupancyStatus.CONFIRMED)));

		assertThat(useCase.getAvailability(LAURA, NEXT_MONDAY).getSlots()).hasSize(12);
	}

	@Test
	void ignoresOccupanciesOfAnotherDate() {
		when(occupancyPort.getOccupancies(LAURA, NEXT_MONDAY))
				.thenReturn(List.of(occupancy(TODAY, "11:00", OccupancyStatus.CONFIRMED)));

		assertThat(starts(useCase.getAvailability(LAURA, NEXT_MONDAY))).startsWith("11:00");
	}

	@Test
	void ordersTheSlotsOfSeveralSchedulesOfTheSameDayWithoutRepeatingThem() {
		// Dos horarios del lunes, desordenados y superpuestos en 10:00.
		when(catalogClientPort.getProfessionalAgenda(LAURA)).thenReturn(Optional.of(lauraWith(List.of(
				schedule(DayOfWeek.MONDAY, "16:00", "17:00", 30),
				schedule(DayOfWeek.MONDAY, "09:30", "10:30", 30),
				schedule(DayOfWeek.MONDAY, "10:00", "10:30", 30)))));

		assertThat(starts(useCase.getAvailability(LAURA, NEXT_MONDAY)))
				.containsExactly("09:30", "10:00", "16:00", "16:30");
	}

	@Test
	void doesNotAskTheCatedraWhenTheCatalogIsUnavailable() {
		when(catalogClientPort.getProfessionalAgenda(LAURA))
				.thenThrow(new CatalogUnavailableException("Catalog service unavailable (HTTP 503)"));

		assertThatThrownBy(() -> useCase.getAvailability(LAURA, NEXT_MONDAY))
				.isInstanceOf(CatalogUnavailableException.class);
		verify(occupancyPort, never()).getOccupancies(anyLong(), any());
	}

	@Test
	void doesNotOfferSlotsWhenTheCatedraIsUnavailable() {
		when(occupancyPort.getOccupancies(LAURA, NEXT_MONDAY))
				.thenThrow(new IntegrationUnavailableException("Catedra service unavailable (HTTP 503)"));

		assertThatThrownBy(() -> useCase.getAvailability(LAURA, NEXT_MONDAY))
				.isInstanceOf(IntegrationUnavailableException.class);
	}

	private void assertRejected(LocalDate date, AvailabilityException.Code code) {
		assertThatThrownBy(() -> useCase.getAvailability(LAURA, date))
				.isInstanceOf(AvailabilityException.class)
				.extracting("code").isEqualTo(code);
	}

	private GetAvailabilityUseCaseImpl useCaseAt(Instant now) {
		return new GetAvailabilityUseCaseImpl(catalogClientPort, occupancyPort,
				AvailabilityHorizon.builder().days(60).build(), Clock.fixed(now, ZoneOffset.UTC));
	}

	private static ProfessionalAgenda laura() {
		return lauraWith(List.of(
				schedule(DayOfWeek.MONDAY, "11:00", "15:00", 20),
				schedule(DayOfWeek.FRIDAY, "12:00", "16:00", 20)));
	}

	private static ProfessionalAgenda lauraWith(List<WeeklySchedule> schedules) {
		return ProfessionalAgenda.builder()
				.id(LAURA)
				.firstName("Laura")
				.lastName("Medina")
				.category(Category.builder().id(102L).name("Pediatria").build())
				.enabled(true)
				.weeklySchedules(schedules)
				.catalogVersion(1L)
				.build();
	}

	private static WeeklySchedule schedule(DayOfWeek day, String start, String end, int duration) {
		return WeeklySchedule.builder()
				.dayOfWeek(day)
				.startTime(LocalTime.parse(start))
				.endTime(LocalTime.parse(end))
				.slotDurationMinutes(duration)
				.build();
	}

	private static Occupancy occupancy(LocalDate date, String start, OccupancyStatus status) {
		return Occupancy.builder().date(date).startTime(LocalTime.parse(start)).status(status).build();
	}

	private static List<String> starts(Availability availability) {
		return availability.getSlots().stream().map(Slot::getStartTime).map(LocalTime::toString).toList();
	}

}
