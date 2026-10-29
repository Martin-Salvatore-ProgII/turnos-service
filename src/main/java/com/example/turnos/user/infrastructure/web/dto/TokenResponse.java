package com.example.turnos.user.infrastructure.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TokenResponse {

	// "id_token" es la excepción al camelCase del contrato: es el nombre que usa JHipster.
	@JsonProperty("id_token")
	private String idToken;
}
