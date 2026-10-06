package com.example.turnos.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.turnos.TestJwtKeysConfiguration;
import com.example.turnos.TestcontainersConfiguration;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Recorrido completo de un usuario: se registra, inicia sesión y usa el token, con la aplicación
 * entera, PostgreSQL real y la seguridad activa. Cada prueba usa un login propio porque todas
 * comparten la misma base.
 *
 * <p>El administrador se configura acá como lo haría el {@code .env}: el servicio lo crea al
 * arrancar (ADR-0061).
 */
@SpringBootTest(properties = {
		"turnos.security.admin.login=admin",
		"turnos.security.admin.password=clave-de-admin",
		"turnos.security.admin.email=admin@example.com" })
@AutoConfigureMockMvc
@Import({ TestcontainersConfiguration.class, TestJwtKeysConfiguration.class, UserAuthenticationFlowTest.TestController.class })
class UserAuthenticationFlowTest {

	private static final String PASSWORD = "secret-1234";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcClient jdbcClient;

	// --- Registro e inicio de sesión (HU-01, HU-02) ---

	@Test
	void registeredUserCanLogInAndUseTheTokenOnAProtectedRoute() throws Exception {
		register("juan", PASSWORD).andExpect(status().isCreated());

		String token = logIn("juan", PASSWORD);

		mockMvc.perform(get("/api/test/whoami").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(content().string("juan"));
	}

	@Test
	void loginIsNotCaseSensitive() throws Exception {
		register("Maria.Lopez", PASSWORD).andExpect(status().isCreated());

		String token = logIn("MARIA.LOPEZ", PASSWORD);

		mockMvc.perform(get("/api/test/whoami").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(content().string("maria.lopez"));
	}

	@Test
	void passwordIsStoredAsAHashAndNeverInPlainText() throws Exception {
		register("pedro", PASSWORD).andExpect(status().isCreated());

		String storedHash = jdbcClient.sql("SELECT password_hash FROM app_user WHERE login = 'pedro'")
				.query(String.class)
				.single();

		assertThat(storedHash).isNotEqualTo(PASSWORD).doesNotContain(PASSWORD).startsWith("$2");
	}

	@Test
	void wrongPasswordAndUnknownLoginGetTheSameAnswer() throws Exception {
		register("lucia", PASSWORD).andExpect(status().isCreated());

		String wrongPassword = authenticate("lucia", "otra-clave")
				.andExpect(status().isUnauthorized())
				.andReturn().getResponse().getContentAsString();
		String unknownLogin = authenticate("nadie", "otra-clave")
				.andExpect(status().isUnauthorized())
				.andReturn().getResponse().getContentAsString();

		assertThat(wrongPassword).isEqualTo(unknownLogin);
	}

	@Test
	void duplicatedLoginAndEmailAreRejectedWithTheirCodes() throws Exception {
		register("carla", PASSWORD).andExpect(status().isCreated());

		registerWithEmail("carla", "otra@example.com")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("USERNAME_ALREADY_EXISTS"));
		registerWithEmail("carla2", "carla@example.com")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"));
	}

	// --- Autorización (RNF-04, ADR-0037) ---

	@Test
	void protectedRouteWithoutTokenIsRejected() throws Exception {
		mockMvc.perform(get("/api/test/whoami")).andExpect(status().isUnauthorized());
	}

	@Test
	void commonUserCannotUseAdminRoutes() throws Exception {
		register("sofia", PASSWORD).andExpect(status().isCreated());

		mockMvc.perform(get("/api/admin/test").header(HttpHeaders.AUTHORIZATION, "Bearer " + logIn("sofia", PASSWORD)))
				.andExpect(status().isForbidden());
	}

	@Test
	void userCannotMakeThemselvesAdminWhenRegistering() throws Exception {
		String body = registrationBody("mateo", "mateo@example.com", PASSWORD)
				.replace("{\"login\"", "{\"authorities\":[\"ROLE_ADMIN\"],\"activated\":false,\"login\"");
		mockMvc.perform(post("/api/register").contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isCreated());

		mockMvc.perform(get("/api/admin/test").header(HttpHeaders.AUTHORIZATION, "Bearer " + logIn("mateo", PASSWORD)))
				.andExpect(status().isForbidden());
	}

	@Test
	void adminCreatedAtStartupCanUseAdminRoutes() throws Exception {
		String token = logIn("admin", "clave-de-admin");

		mockMvc.perform(get("/api/admin/test").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isOk());
	}

	@Test
	void adminLoginCannotBeTakenThroughPublicRegistration() throws Exception {
		registerWithEmail("admin", "intruso@example.com")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("USERNAME_ALREADY_EXISTS"));
	}

	private ResultActions register(String login, String password) throws Exception {
		return mockMvc.perform(post("/api/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content(registrationBody(login, login.toLowerCase() + "@example.com", password)));
	}

	private ResultActions registerWithEmail(String login, String email) throws Exception {
		return mockMvc.perform(post("/api/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content(registrationBody(login, email, PASSWORD)));
	}

	private String registrationBody(String login, String email, String password) {
		return """
				{"login":"%s","password":"%s","firstName":"Nombre","lastName":"Apellido","email":"%s","langKey":"es"}
				""".formatted(login, password, email);
	}

	private ResultActions authenticate(String username, String password) throws Exception {
		return mockMvc.perform(post("/api/authenticate")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, password)));
	}

	private String logIn(String username, String password) throws Exception {
		String response = authenticate(username, password)
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		return JsonPath.read(response, "$.id_token");
	}

	// Rutas solo para estas pruebas: el servicio todavía no tiene endpoints protegidos propios.
	@RestController
	static class TestController {

		@GetMapping("/api/test/whoami")
		String whoAmI(Authentication authentication) {
			return authentication.getName();
		}

		@GetMapping("/api/admin/test")
		String adminRoute() {
			return "admin";
		}

	}

}
