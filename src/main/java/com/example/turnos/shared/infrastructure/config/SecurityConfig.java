package com.example.turnos.shared.infrastructure.config;

import java.util.List;

import jakarta.servlet.DispatcherType;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Reglas de acceso del servicio. Todo pedido exige un JWT de usuario válido, salvo lo declarado
 * público acá (RNF-04).
 */
@Configuration
@EnableConfigurationProperties(CorsProperties.class)
public class SecurityConfig {

	private static final String ROLE_ADMIN = "ROLE_ADMIN";

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http
				// CSRF protege formularios de sitios que identifican al usuario con una cookie de
				// sesión. Acá no hay cookies ni sesión: cada pedido trae su token en un encabezado.
				.csrf(AbstractHttpConfigurer::disable)
				.cors(Customizer.withDefaults())
				// Sin sesión en el servidor: el token es la única prueba de identidad.
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(authorize -> authorize
						// Lo único público: registrarse, iniciar sesión y saber si el servicio está en pie.
						.requestMatchers(HttpMethod.POST, "/api/register", "/api/authenticate").permitAll()
						.requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**").permitAll()
						// El despacho interno de errores no es un pedido nuevo del cliente: si se
						// protegiera, un error ya resuelto se convertiría en un 401.
						.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
						// Las operaciones administrativas se protegen por ruta, no dentro de cada
						// operación (ADR-0037).
						.requestMatchers("/api/admin/**").hasAuthority(ROLE_ADMIN)
						// Protegido por defecto: cualquier ruta nueva nace exigiendo token.
						.anyRequest().authenticated())
				// Valida el JWT del encabezado Authorization: firma, vencimiento y roles.
				.oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));
		return http.build();
	}

	// Los roles vienen en el claim "auth" tal cual se usan (ROLE_USER, ROLE_ADMIN). Por defecto
	// Spring los buscaría en otro claim y les agregaría un prefijo.
	@Bean
	public JwtAuthenticationConverter jwtAuthenticationConverter() {
		JwtGrantedAuthoritiesConverter authoritiesConverter = new JwtGrantedAuthoritiesConverter();
		authoritiesConverter.setAuthoritiesClaimName(JwtConfig.AUTHORITIES_CLAIM);
		authoritiesConverter.setAuthorityPrefix("");
		JwtAuthenticationConverter authenticationConverter = new JwtAuthenticationConverter();
		authenticationConverter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);
		return authenticationConverter;
	}

	// CORS cerrado: la lista de orígenes permitidos es configurable y está vacía (ADR-0036).
	// La app Android no es un navegador y no se ve afectada.
	@Bean
	public CorsConfigurationSource corsConfigurationSource(CorsProperties corsProperties) {
		CorsConfiguration configuration = new CorsConfiguration();
		configuration.setAllowedOrigins(corsProperties.allowedOrigins() == null ? List.of() : corsProperties.allowedOrigins());
		configuration.setAllowedMethods(List.of("GET", "POST"));
		configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", configuration);
		return source;
	}

}
