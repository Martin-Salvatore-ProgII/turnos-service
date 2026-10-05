package com.example.turnos.shared.infrastructure.web.advice;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

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

	@Test
	void malformedJsonReturnsProblemWithoutCode() throws Exception {
		mockMvc.perform(post("/test/items").contentType(MediaType.APPLICATION_JSON).content("{"))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
				.andExpect(jsonPath("$.code").doesNotExist());
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

		@GetMapping("/test/failure")
		void fail() {
			throw new IllegalStateException("internal detail");
		}

	}

	record TestRequest(@NotBlank(message = "Name is mandatory") String name) {
	}

}
