package com.example.turnos.availability.infrastructure.web.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import com.example.turnos.availability.application.exception.AvailabilityException;
import com.example.turnos.availability.application.service.AvailabilityService;
import com.example.turnos.availability.domain.model.Availability;
import com.example.turnos.availability.domain.model.Slot;
import com.example.turnos.availability.infrastructure.web.mapper.AvailabilityDtoMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

// Los parámetros, la forma de la respuesta y los códigos HTTP, con la fachada simulada. Sin los
// filtros de seguridad: el acceso se prueba en AvailabilityFlowTest.
@AutoConfigureMockMvc(addFilters = false)
@WebMvcTest(AvailabilityController.class)
@Import(AvailabilityDtoMapper.class)
class AvailabilityControllerTest {

	private static final LocalDate DATE = LocalDate.parse("2026-11-09");

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private AvailabilityService availabilityService;

	@Test
	void returnsTheAvailabilityWithTheShapeOfTheContract() throws Exception {
		when(availabilityService.getAvailability(204L, DATE)).thenReturn(Availability.builder()
				.professionalId(204L).professionalFirstName("Laura").professionalLastName("Medina")
				.date(DATE).catalogVersion(1L)
				.slots(List.of(Slot.builder().startTime(LocalTime.parse("11:00")).endTime(LocalTime.parse("11:20")).build()))
				.build());

		mockMvc.perform(get("/api/availability").param("professionalId", "204").param("date", "2026-11-09"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.professionalId").value(204))
				.andExpect(jsonPath("$.professionalFirstName").value("Laura"))
				.andExpect(jsonPath("$.professionalLastName").value("Medina"))
				.andExpect(jsonPath("$.date").value("2026-11-09"))
				.andExpect(jsonPath("$.catalogVersion").value(1))
				// Con segundos, aunque la hora sea en punto (contratos.md).
				.andExpect(jsonPath("$.slots[0].startTime").value("11:00:00"))
				.andExpect(jsonPath("$.slots[0].endTime").value("11:20:00"));
	}

	@Test
	void anEmptyListIsAValidResponse() throws Exception {
		when(availabilityService.getAvailability(204L, DATE)).thenReturn(Availability.builder()
				.professionalId(204L).date(DATE).slots(List.of()).build());

		mockMvc.perform(get("/api/availability").param("professionalId", "204").param("date", "2026-11-09"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.slots", hasSize(0)));
	}

	@Test
	void bothParametersAreRequired() throws Exception {
		mockMvc.perform(get("/api/availability"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.fieldErrors", hasSize(2)));
		verifyNoInteractions(availabilityService);
	}

	@Test
	void rejectsADateWithAnInvalidFormatWithoutShowingInternalDetails() throws Exception {
		mockMvc.perform(get("/api/availability").param("professionalId", "204").param("date", "2026-13-40"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.fieldErrors[0].field").value("date"))
				.andExpect(jsonPath("$.fieldErrors[0].message").value("has an invalid format"))
				.andExpect(content().string(not(containsString("java."))));
		verifyNoInteractions(availabilityService);
	}

	@Test
	void rejectsAProfessionalIdThatIsNotANumber() throws Exception {
		mockMvc.perform(get("/api/availability").param("professionalId", "abc").param("date", "2026-11-09"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.fieldErrors[0].field").value("professionalId"));
	}

	@Test
	void reportsTheRejectionOfTheUseCaseWithItsCode() throws Exception {
		when(availabilityService.getAvailability(204L, DATE)).thenThrow(new AvailabilityException(
				AvailabilityException.Code.DATE_OUT_OF_RANGE, "Date 2026-11-09 must be between ..."));

		mockMvc.perform(get("/api/availability").param("professionalId", "204").param("date", "2026-11-09"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("DATE_OUT_OF_RANGE"));
	}

}
