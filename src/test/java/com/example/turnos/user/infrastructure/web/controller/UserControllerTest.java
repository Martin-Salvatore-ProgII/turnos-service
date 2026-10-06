package com.example.turnos.user.infrastructure.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.turnos.user.application.exception.UserException;
import com.example.turnos.user.application.service.UserService;
import com.example.turnos.user.domain.model.User;
import com.example.turnos.user.infrastructure.web.mapper.UserDtoMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@WebMvcTest(UserController.class)
@Import(UserDtoMapper.class)
class UserControllerTest {

	private static final String VALID_BODY = """
			{"login":"juan","password":"secret-1234","firstName":"Juan","lastName":"Perez",
			 "email":"juan@example.com","imageUrl":"https://example.com/juan.png","langKey":"es"}
			""";

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private UserService userService;

	@Test
	void validRegistrationReturnsCreatedWithoutBody() throws Exception {
		register(VALID_BODY)
				.andExpect(status().isCreated())
				.andExpect(content().string(""));

		ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
		verify(userService).registerUser(captor.capture(), eq("secret-1234"));
		assertThat(captor.getValue().getLogin()).isEqualTo("juan");
		assertThat(captor.getValue().getEmail()).isEqualTo("juan@example.com");
		assertThat(captor.getValue().getPasswordHash()).isNull();
	}

	@Test
	void imageUrlIsOptional() throws Exception {
		register(VALID_BODY.replace("\"imageUrl\":\"https://example.com/juan.png\",", ""))
				.andExpect(status().isCreated());
	}

	@Test
	void ignoresFieldsTheClientCannotChoose() throws Exception {
		register(VALID_BODY.replace("{\"login\"", "{\"id\":99,\"activated\":false,\"authorities\":[\"ROLE_ADMIN\"],\"login\""))
				.andExpect(status().isCreated());

		ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
		verify(userService).registerUser(captor.capture(), any());
		assertThat(captor.getValue().getId()).isNull();
		assertThat(captor.getValue().getAuthorities()).isNull();
	}

	// Cada fila reemplaza un fragmento del cuerpo válido por uno inválido e indica el campo rechazado.
	@ParameterizedTest
	@CsvSource(delimiter = '|', textBlock = """
			"login":"juan"              | "login":""                  | login
			"login":"juan"              | "login":"juan perez"        | login
			"password":"secret-1234"    | "password":"abc"            | password
			"firstName":"Juan"          | "firstName":"J"             | firstName
			"lastName":"Perez"          | "lastName":"P"              | lastName
			"email":"juan@example.com"  | "email":"no-es-un-email"    | email
			"langKey":"es"              | "langKey":"e"               | langKey
			""")
	void invalidFieldReturnsValidationError(String valid, String invalid, String field) throws Exception {
		register(VALID_BODY.replace(valid, invalid))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.fieldErrors[?(@.field == '%s')]".formatted(field)).exists());

		verify(userService, never()).registerUser(any(), any());
	}

	@Test
	void missingPasswordReturnsValidationError() throws Exception {
		register(VALID_BODY.replace("\"password\":\"secret-1234\",", ""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.fieldErrors[0].field").value("password"));
	}

	@Test
	void passwordLongerThan72BytesReturnsValidationError() throws Exception {
		// 37 letras "ñ" son 37 caracteres pero 74 bytes.
		register(VALID_BODY.replace("secret-1234", "ñ".repeat(37)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.fieldErrors[0].field").value("password"));
	}

	@Test
	void passwordOf72BytesIsAccepted() throws Exception {
		register(VALID_BODY.replace("secret-1234", "a".repeat(72)))
				.andExpect(status().isCreated());
	}

	@Test
	void loginAlreadyInUseReturnsItsCode() throws Exception {
		when(userService.registerUser(any(), any()))
				.thenThrow(new UserException(UserException.Code.USERNAME_ALREADY_EXISTS, "Login already in use"));

		register(VALID_BODY)
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith("application/problem+json"))
				.andExpect(jsonPath("$.code").value("USERNAME_ALREADY_EXISTS"));
	}

	@Test
	void emailAlreadyInUseReturnsItsCode() throws Exception {
		when(userService.registerUser(any(), any()))
				.thenThrow(new UserException(UserException.Code.EMAIL_ALREADY_EXISTS, "Email already in use"));

		register(VALID_BODY)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"));
	}

	@Test
	void errorResponseNeverContainsThePassword() throws Exception {
		register(VALID_BODY.replace("\"email\":\"juan@example.com\"", "\"email\":\"no-es-un-email\""))
				.andExpect(status().isBadRequest())
				.andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret-1234"))));
	}

	private ResultActions register(String body) throws Exception {
		return mockMvc.perform(post("/api/register").contentType(MediaType.APPLICATION_JSON).content(body));
	}

}
