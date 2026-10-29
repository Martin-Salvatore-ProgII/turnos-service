package com.example.turnos.shared.infrastructure.web.advice;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.turnos.availability.application.exception.AvailabilityException;
import com.example.turnos.availability.application.exception.IntegrationUnavailableException;
import com.example.turnos.catalog.application.exception.CatalogTokenRejectedException;
import com.example.turnos.catalog.application.exception.CatalogUnavailableException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// Sin los filtros de seguridad: acá se prueba el manejador de errores. Las reglas de acceso se prueban en SecurityConfigTest.
@AutoConfigureMockMvc(addFilters = false)
@WebMvcTest(controllers = GlobalExceptionHandlerTest.TestController.class)
@Import(GlobalExceptionHandlerTest.TestController.class)
class GlobalExceptionHandlerTest {

	private static final String PROBLEM_JSON = "application/problem+json";

	@Autowired
	private MockMvc mockMvc;

	@Test
	void invalidBodyReturnsValidationErrorWithFieldErrors() throws Exception {
		mockMvc.perform(post("/test/items").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
				.andExpect(jsonPath("$.status").value(400))
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.path").value("/test/items"))
				.andExpect(jsonPath("$.fieldErrors[0].objectName").value("testRequest"))
				.andExpect(jsonPath("$.fieldErrors[0].field").value("name"))
				.andExpect(jsonPath("$.fieldErrors[0].message").value("Name is mandatory"));
	}

	// El contrato no exime de code a ningún 400: lo que arma Spring también es VALIDATION_ERROR.
	@Test
	void malformedJsonReturnsValidationError() throws Exception {
		mockMvc.perform(post("/test/items").contentType(MediaType.APPLICATION_JSON).content("{"))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
	}

	@Test
	void invalidPathVariableReturnsValidationErrorWithTheField() throws Exception {
		mockMvc.perform(get("/test/items/abc"))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.path").value("/test/items/abc"))
				.andExpect(jsonPath("$.fieldErrors[0].field").value("id"))
				.andExpect(jsonPath("$.fieldErrors[0].objectName").doesNotExist());
	}

	@Test
	void missingRequiredParameterReturnsValidationError() throws Exception {
		mockMvc.perform(get("/test/required"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
	}

	@Test
	void unknownRouteReturnsNotFoundProblem() throws Exception {
		mockMvc.perform(get("/does-not-exist"))
				.andExpect(status().isNotFound())
				.andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
				.andExpect(jsonPath("$.status").value(404))
				.andExpect(jsonPath("$.path").value("/does-not-exist"))
				.andExpect(jsonPath("$.code").doesNotExist());
	}

	@Test
	void methodNotAllowedReturnsProblem() throws Exception {
		mockMvc.perform(post("/test/failure"))
				.andExpect(status().isMethodNotAllowed())
				.andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON));
	}

	@Test
	void unavailableCatalogReturnsCatalogUnavailable() throws Exception {
		mockMvc.perform(get("/test/catalog-unavailable"))
				.andExpect(status().isServiceUnavailable())
				.andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
				.andExpect(jsonPath("$.code").value("CATALOG_UNAVAILABLE"))
				.andExpect(jsonPath("$.detail").value("Catalog service unavailable (HTTP 503)"))
				.andExpect(jsonPath("$.path").value("/test/catalog-unavailable"));
	}

	@Test
	void dateOutOfRangeReturnsBadRequestWithItsCode() throws Exception {
		mockMvc.perform(get("/test/availability/DATE_OUT_OF_RANGE"))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
				.andExpect(jsonPath("$.code").value("DATE_OUT_OF_RANGE"));
	}

	@Test
	void disabledProfessionalReturnsBadRequestWithItsCode() throws Exception {
		mockMvc.perform(get("/test/availability/PROFESSIONAL_DISABLED"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("PROFESSIONAL_DISABLED"));
	}

	@Test
	void missingProfessionalReturnsNotFoundWithItsCode() throws Exception {
		mockMvc.perform(get("/test/availability/PROFESSIONAL_NOT_FOUND"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("PROFESSIONAL_NOT_FOUND"))
				.andExpect(jsonPath("$.path").value("/test/availability/PROFESSIONAL_NOT_FOUND"));
	}

	@Test
	void unavailableIntegrationReturnsIntegrationUnavailable() throws Exception {
		mockMvc.perform(get("/test/integration-unavailable"))
				.andExpect(status().isServiceUnavailable())
				.andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
				.andExpect(jsonPath("$.code").value("INTEGRATION_UNAVAILABLE"))
				.andExpect(jsonPath("$.detail").value("Catedra service unavailable (HTTP 503)"));
	}

	// Como cualquier token vencido: 401 sin code, con WWW-Authenticate (ADR-0034, contratos.md).
	@Test
	void tokenRejectedByTheCatalogReturnsUnauthorizedWithoutCode() throws Exception {
		mockMvc.perform(get("/test/catalog-token-rejected"))
				.andExpect(status().isUnauthorized())
				.andExpect(header().string("WWW-Authenticate", "Bearer error=\"invalid_token\""))
				.andExpect(jsonPath("$.code").doesNotExist());
	}

	@Test
	void unexpectedExceptionReturnsGenericInternalError() throws Exception {
		mockMvc.perform(get("/test/failure"))
				.andExpect(status().isInternalServerError())
				.andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
				.andExpect(jsonPath("$.status").value(500))
				.andExpect(jsonPath("$.detail").value("Unexpected error"))
				.andExpect(jsonPath("$.path").value("/test/failure"))
				.andExpect(jsonPath("$.code").doesNotExist())
				.andExpect(content().string(not(containsString("internal detail"))));
	}

	// Controller solo para estas pruebas: el servicio todavía no tiene endpoints propios.
	@RestController
	static class TestController {

		@PostMapping("/test/items")
		void create(@Valid @RequestBody TestRequest request) {
		}

		@GetMapping("/test/items/{id}")
		void find(@PathVariable Long id) {
		}

		@GetMapping("/test/required")
		void required(@RequestParam String value) {
		}

		@GetMapping("/test/catalog-unavailable")
		void catalogUnavailable() {
			throw new CatalogUnavailableException("Catalog service unavailable (HTTP 503)");
		}

		@GetMapping("/test/availability/{code}")
		void availabilityRejected(@PathVariable AvailabilityException.Code code) {
			throw new AvailabilityException(code, "rejected");
		}

		@GetMapping("/test/integration-unavailable")
		void integrationUnavailable() {
			throw new IntegrationUnavailableException("Catedra service unavailable (HTTP 503)");
		}

		@GetMapping("/test/catalog-token-rejected")
		void catalogTokenRejected() {
			throw new CatalogTokenRejectedException();
		}

		@GetMapping("/test/failure")
		void fail() {
			throw new IllegalStateException("internal detail");
		}

	}

	record TestRequest(@NotBlank(message = "Name is mandatory") String name) {
	}

}
