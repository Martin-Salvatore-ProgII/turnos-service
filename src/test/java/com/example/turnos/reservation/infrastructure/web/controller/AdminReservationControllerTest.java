package com.example.turnos.reservation.infrastructure.web.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import com.example.turnos.reservation.application.exception.ReservationException;
import com.example.turnos.reservation.application.service.ReservationService;
import com.example.turnos.reservation.domain.model.PageResult;
import com.example.turnos.reservation.domain.model.ReservationOwner;
import com.example.turnos.reservation.domain.model.ReservationProcess;
import com.example.turnos.reservation.domain.model.ReservationStatus;
import com.example.turnos.reservation.domain.model.ReservationWithOwner;
import com.example.turnos.reservation.infrastructure.web.mapper.ReservationDtoMapper;
import com.example.turnos.shared.infrastructure.config.PaginationConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

// Los parámetros, la forma de la respuesta administrativa y los 400, con la fachada simulada. Sin los
// filtros de seguridad: el 403 de un usuario común se prueba con la seguridad real (#74).
@AutoConfigureMockMvc(addFilters = false)
@WebMvcTest(AdminReservationController.class)
@Import({ ReservationDtoMapper.class, PaginationConfig.class })
class AdminReservationControllerTest {

	private static final UUID ID = UUID.fromString("5b1f0c2e-8d7a-4a51-9e0b-1f2d3c4b5a69");

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private ReservationService reservationService;

	@Test
	void listsWithTheRepresentationOfAReservationPlusTheSupportData() throws Exception {
		when(reservationService.listAllReservations(any(), any())).thenReturn(page(1, cancelledReservation()));

		mockMvc.perform(get("/api/admin/reservations"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(1)))
				// Los campos de la reserva van al mismo nivel que los nuevos, no anidados.
				.andExpect(jsonPath("$[0].id").value(ID.toString()))
				.andExpect(jsonPath("$[0].status").value("CANCELLED"))
				.andExpect(jsonPath("$[0].professionalFirstName").value("Laura"))
				.andExpect(jsonPath("$[0].startTime").value("11:20:00"))
				.andExpect(jsonPath("$[0].failureReason").doesNotExist())
				.andExpect(jsonPath("$[0].cancellationReason").value("No puedo asistir"))
				.andExpect(jsonPath("$[0].reservation").doesNotExist())
				.andExpect(jsonPath("$[0].owner.login").value("juan"))
				.andExpect(jsonPath("$[0].owner.firstName").value("Juan"))
				.andExpect(jsonPath("$[0].owner.lastName").value("Perez"))
				.andExpect(jsonPath("$[0].reservationProcessId").value("process-a92b47d0"))
				.andExpect(jsonPath("$[0].cancelledBy").value("juan"));
	}

	@Test
	void withoutParametersAsksForTheFirstPageOfTheDefaultSizeWithoutFilters() throws Exception {
		when(reservationService.listAllReservations(any(), any())).thenReturn(page(0));

		mockMvc.perform(get("/api/admin/reservations"))
				.andExpect(status().isOk())
				.andExpect(header().string("X-Total-Count", "0"));

		verify(reservationService).listAllReservations(
				argThat(c -> c.getOwnerLogin() == null && c.getOwnerId() == null && c.getStatus() == null
						&& c.getFrom() == null && c.getTo() == null),
				argThat(p -> p.getPage() == 0 && p.getSize() == 20));
	}

	@Test
	void passesEveryFilterAndThePageToTheService() throws Exception {
		when(reservationService.listAllReservations(any(), any())).thenReturn(page(6, cancelledReservation()));

		mockMvc.perform(get("/api/admin/reservations")
						.param("ownerLogin", "juan")
						.param("status", "CONFIRMED")
						.param("from", "2026-11-01")
						.param("to", "2026-11-30")
						.param("page", "1")
						.param("size", "2"))
				.andExpect(status().isOk())
				.andExpect(header().string("X-Total-Count", "6"))
				.andExpect(header().string("Link", containsString("rel=\"prev\"")));

		verify(reservationService).listAllReservations(
				argThat(c -> "juan".equals(c.getOwnerLogin()) && c.getStatus() == ReservationStatus.CONFIRMED
						&& c.getFrom().equals(LocalDate.parse("2026-11-01"))
						&& c.getTo().equals(LocalDate.parse("2026-11-30"))),
				argThat(p -> p.getPage() == 1 && p.getSize() == 2));
	}

	// El mismo día en los dos extremos es un rango válido; al revés, no.
	@Test
	void aRangeOfASingleDayIsValid() throws Exception {
		when(reservationService.listAllReservations(any(), any())).thenReturn(page(0));

		mockMvc.perform(get("/api/admin/reservations").param("from", "2026-11-10").param("to", "2026-11-10"))
				.andExpect(status().isOk());
	}

	@Test
	void aRangeThatEndsBeforeItStartsIsAValidationError() throws Exception {
		mockMvc.perform(get("/api/admin/reservations").param("from", "2026-11-30").param("to", "2026-11-01"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.fieldErrors[0].field").value("dateRangeOrdered"));

		verifyNoInteractions(reservationService);
	}

	@Test
	void anUnknownStatusIsAValidationError() throws Exception {
		mockMvc.perform(get("/api/admin/reservations").param("status", "PAID"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.fieldErrors[0].field").value("status"));

		verifyNoInteractions(reservationService);
	}

	@Test
	void aDateWithAnotherFormatIsAValidationError() throws Exception {
		mockMvc.perform(get("/api/admin/reservations").param("from", "10/11/2026"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.fieldErrors[0].field").value("from"));

		verifyNoInteractions(reservationService);
	}

	@Test
	void aBlankOwnerLoginIsAValidationError() throws Exception {
		mockMvc.perform(get("/api/admin/reservations").param("ownerLogin", "  "))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

		verifyNoInteractions(reservationService);
	}

	@Test
	void aPageSizeOverTheMaximumIsAValidationError() throws Exception {
		mockMvc.perform(get("/api/admin/reservations").param("size", "1000"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

		verifyNoInteractions(reservationService);
	}

	@Test
	void showsAnyReservationWithTheSupportData() throws Exception {
		when(reservationService.getAnyReservation(ID)).thenReturn(cancelledReservation());

		mockMvc.perform(get("/api/admin/reservations/" + ID))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(ID.toString()))
				.andExpect(jsonPath("$.status").value("CANCELLED"))
				.andExpect(jsonPath("$.owner.login").value("juan"))
				.andExpect(jsonPath("$.reservationProcessId").value("process-a92b47d0"))
				.andExpect(jsonPath("$.cancelledBy").value("juan"));
	}

	@Test
	void aMissingReservationIsNotFound() throws Exception {
		when(reservationService.getAnyReservation(ID)).thenThrow(new ReservationException(
				ReservationException.Code.RESERVATION_NOT_FOUND, "Could not find reservation with id: " + ID));

		mockMvc.perform(get("/api/admin/reservations/" + ID))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("RESERVATION_NOT_FOUND"));
	}

	@Test
	void anIdThatIsNotAUuidIsAValidationError() throws Exception {
		mockMvc.perform(get("/api/admin/reservations/abc"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.fieldErrors[0].field").value("id"));

		verifyNoInteractions(reservationService);
	}

	private static PageResult<ReservationWithOwner> page(long total, ReservationWithOwner... reservations) {
		return PageResult.<ReservationWithOwner>builder()
				.content(List.of(reservations))
				.totalElements(total)
				.page(reservations.length == 0 ? 0 : 1)
				.size(reservations.length == 0 ? 20 : 2)
				.build();
	}

	private static ReservationWithOwner cancelledReservation() {
		ReservationProcess process = ReservationProcess.builder()
				.id(ID)
				.userId(1L)
				.status(ReservationStatus.CANCELLED)
				.professionalId(204L)
				.professionalFirstName("Laura")
				.professionalLastName("Medina")
				.appointmentDate(LocalDate.parse("2026-11-09"))
				.startTime(LocalTime.parse("11:20"))
				.endTime(LocalTime.parse("11:40"))
				.reservationProcessId("process-a92b47d0")
				.cancellationReason("No puedo asistir")
				.cancelledBy("juan")
				.createdAt(Instant.parse("2026-11-02T15:30:00Z"))
				.cancelledAt(Instant.parse("2026-11-03T10:00:00Z"))
				.build();
		ReservationOwner owner = ReservationOwner.builder().login("juan").firstName("Juan").lastName("Perez").build();
		return ReservationWithOwner.builder().process(process).owner(owner).build();
	}

}
