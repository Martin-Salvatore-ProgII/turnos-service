package com.example.turnos.reservation.infrastructure.web.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import com.example.turnos.catalog.application.exception.CatalogUnavailableException;
import com.example.turnos.reservation.application.exception.ReservationException;
import com.example.turnos.reservation.application.service.ReservationService;
import com.example.turnos.reservation.domain.model.ReservationProcess;
import com.example.turnos.reservation.domain.model.ReservationStatus;
import com.example.turnos.reservation.infrastructure.web.mapper.ReservationDtoMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

// El cuerpo, la forma de la respuesta y los códigos HTTP, con la fachada simulada. Sin los filtros de
// seguridad: el usuario se pasa como principal del pedido, y el acceso se prueba en ReservationFlowTest.
@AutoConfigureMockMvc(addFilters = false)
@WebMvcTest(ReservationController.class)
@Import(ReservationDtoMapper.class)
class ReservationControllerTest {

	private static final UUID ID = UUID.fromString("5b1f0c2e-8d7a-4a51-9e0b-1f2d3c4b5a69");
	private static final LocalDate DATE = LocalDate.parse("2026-11-09");

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private ReservationService reservationService;

	@Test
	void startsTheReservationOfTheUserOfTheTokenWithTheShapeOfTheContract() throws Exception {
		when(reservationService.startReservation("juan", 204L, DATE, LocalTime.parse("11:20")))
				.thenReturn(process());

		mockMvc.perform(asJuan(post("/api/reservations")).contentType(MediaType.APPLICATION_JSON)
						.content(body("11:20")))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").value(ID.toString()))
				.andExpect(jsonPath("$.status").value("AWAITING_REQUEST"))
				.andExpect(jsonPath("$.professionalId").value(204))
				.andExpect(jsonPath("$.professionalFirstName").value("Laura"))
				.andExpect(jsonPath("$.professionalLastName").value("Medina"))
				.andExpect(jsonPath("$.date").value("2026-11-09"))
				// Con segundos, aunque la hora sea en punto (contratos.md).
				.andExpect(jsonPath("$.startTime").value("11:20:00"))
				.andExpect(jsonPath("$.endTime").value("11:40:00"))
				.andExpect(jsonPath("$.expiresAt").value("2026-11-02T15:41:00Z"))
				.andExpect(jsonPath("$.message").value("Ingrese un numero de telefono para continuar."))
				.andExpect(jsonPath("$.createdAt").value("2026-11-02T15:30:00Z"))
				// Los campos sin valor van como null, como en el ejemplo del contrato.
				.andExpect(jsonPath("$.failureReason").isEmpty())
				.andExpect(jsonPath("$.confirmedAt").isEmpty())
				.andExpect(content().string(not(containsString("hold"))))
				.andExpect(content().string(not(containsString("version"))));
	}

	@Test
	void acceptsTheStartTimeWithSeconds() throws Exception {
		when(reservationService.startReservation("juan", 204L, DATE, LocalTime.parse("11:20"))).thenReturn(process());

		mockMvc.perform(asJuan(post("/api/reservations")).contentType(MediaType.APPLICATION_JSON)
						.content(body("11:20:00")))
				.andExpect(status().isCreated());
	}

	@Test
	void allTheFieldsAreRequired() throws Exception {
		mockMvc.perform(asJuan(post("/api/reservations")).contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.fieldErrors", hasSize(3)));
		verifyNoInteractions(reservationService);
	}

	@Test
	void rejectsATimeWithAnInvalidFormat() throws Exception {
		mockMvc.perform(asJuan(post("/api/reservations")).contentType(MediaType.APPLICATION_JSON)
						.content(body("25:00")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(content().string(not(containsString("java."))));
		verifyNoInteractions(reservationService);
	}

	@ParameterizedTest
	@CsvSource({ "DATE_OUT_OF_RANGE, 400", "PROFESSIONAL_DISABLED, 400", "INVALID_SLOT, 400",
			"PROFESSIONAL_NOT_FOUND, 404", "ACTIVE_RESERVATION_EXISTS, 409" })
	void eachRejectionHasTheStatusOfTheContract(ReservationException.Code code, int status) throws Exception {
		when(reservationService.startReservation(eq("juan"), any(), any(), any()))
				.thenThrow(new ReservationException(code, "rejected"));

		mockMvc.perform(asJuan(post("/api/reservations")).contentType(MediaType.APPLICATION_JSON)
						.content(body("11:20")))
				.andExpect(status().is(status))
				.andExpect(jsonPath("$.code").value(code.name()));
	}

	@Test
	void anUnavailableCatalogIsAServiceUnavailable() throws Exception {
		when(reservationService.startReservation(eq("juan"), any(), any(), any()))
				.thenThrow(new CatalogUnavailableException("Catalog service unavailable"));

		mockMvc.perform(asJuan(post("/api/reservations")).contentType(MediaType.APPLICATION_JSON)
						.content(body("11:20")))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.code").value("CATALOG_UNAVAILABLE"));
	}

	@Test
	void returnsTheReservationOfTheUserOfTheToken() throws Exception {
		when(reservationService.getReservation("juan", ID)).thenReturn(process());

		mockMvc.perform(asJuan(get("/api/reservations/" + ID)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(ID.toString()))
				.andExpect(jsonPath("$.status").value("AWAITING_REQUEST"));
	}

	@Test
	void aMissingReservationIsNotFound() throws Exception {
		when(reservationService.getReservation("juan", ID))
				.thenThrow(new ReservationException(ReservationException.Code.RESERVATION_NOT_FOUND, "not found"));

		mockMvc.perform(asJuan(get("/api/reservations/" + ID)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("RESERVATION_NOT_FOUND"));
	}

	@Test
	void anIdThatIsNotAUuidIsAValidationError() throws Exception {
		mockMvc.perform(asJuan(get("/api/reservations/abc")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.fieldErrors[0].field").value("id"));
		verifyNoInteractions(reservationService);
	}

	// El usuario que dejaría la seguridad después de validar el JWT: su login es el nombre.
	private static MockHttpServletRequestBuilder asJuan(MockHttpServletRequestBuilder request) {
		return request.principal(new TestingAuthenticationToken("juan", null, "ROLE_USER"));
	}

	private static String body(String startTime) {
		return """
				{"professionalId":204,"date":"2026-11-09","startTime":"%s"}
				""".formatted(startTime);
	}

	private static ReservationProcess process() {
		return ReservationProcess.builder()
				.id(ID)
				.userId(7L)
				.status(ReservationStatus.AWAITING_REQUEST)
				.professionalId(204L)
				.professionalFirstName("Laura")
				.professionalLastName("Medina")
				.appointmentDate(DATE)
				.startTime(LocalTime.parse("11:20"))
				.endTime(LocalTime.parse("11:40"))
				.holdId("hold-1")
				.reservationProcessId("process-1")
				.expiresAt(Instant.parse("2026-11-02T15:41:00Z"))
				.lastMessage("Ingrese un numero de telefono para continuar.")
				.createdAt(Instant.parse("2026-11-02T15:30:00Z"))
				.version(1L)
				.build();
	}

}
