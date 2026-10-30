package com.example.turnos.reservation.infrastructure.rest.adapter;

import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Set;
import java.util.UUID;

import com.example.turnos.reservation.domain.model.Hold;
import com.example.turnos.reservation.domain.model.HoldResult;
import com.example.turnos.reservation.domain.model.ReservationFailureReason;
import com.example.turnos.reservation.domain.ports.out.HoldPort;
import com.example.turnos.reservation.infrastructure.rest.dto.HoldResponse;
import com.example.turnos.reservation.infrastructure.rest.mapper.HoldMapper;
import com.example.turnos.shared.infrastructure.rest.ProblemDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Slf4j
@Component
@RequiredArgsConstructor
public class CatedraHoldAdapter implements HoldPort {

	static final String HOLDS_PATH = "/api/appointment-holds";

	// Los rechazos de la cátedra al crear un hold (REF §13.1) que significan algo para el usuario: el
	// motivo de FAILED es el mismo code. Cualquier otro 4xx (un VALIDATION_ERROR, el JWT técnico
	// rechazado) sería un error nuestro o de la integración, no del turno elegido.
	private static final Set<String> REJECTION_CODES = Set.of("SLOT_ALREADY_HELD", "SLOT_ALREADY_RESERVED",
			"INVALID_SLOT", "PROFESSIONAL_DISABLED", "PROFESSIONAL_NOT_FOUND");

	private final RestClient catedraRestClient;
	private final HoldMapper holdMapper;

	// Un solo intento, pase lo que pase (ADR-0028, ADR-0069): crear un hold no es seguro de repetir, y
	// un segundo pedido respondería SLOT_ALREADY_HELD sin poder saber si el hold es propio. En el log
	// va solo el tipo de falla: el mensaje original incluye la URL de la cátedra.
	@Override
	public HoldResult createHold(Long professionalId, LocalDate date, LocalTime startTime, UUID externalPatientId) {
		try {
			HoldResponse response = catedraRestClient.post()
					.uri(HOLDS_PATH)
					.body(holdMapper.toRequest(professionalId, date, startTime, externalPatientId))
					.retrieve()
					.body(HoldResponse.class);
			return toResult(holdMapper.toDomainModel(response));
		} catch (HttpClientErrorException e) {
			String code = ProblemDto.codeOf(e);
			// code puede ser null (un 401 sin cuerpo), y Set.of no admite preguntar por null.
			if (code != null && REJECTION_CODES.contains(code)) {
				return HoldResult.failed(code);
			}
			return unavailable("HTTP " + e.getStatusCode().value() + (code == null ? "" : " " + code));
		} catch (HttpServerErrorException e) {
			return unavailable("HTTP " + e.getStatusCode().value());
		} catch (ResourceAccessException e) {
			if (isResponseTimeout(e)) {
				log.warn("Hold request to the catedra timed out: the hold may exist and will expire on its own");
				return HoldResult.failed(ReservationFailureReason.INTEGRATION_TIMEOUT);
			}
			return unavailable(e.getMostSpecificCause().getClass().getSimpleName());
		} catch (RestClientException e) {
			return unavailable("unreadable body");
		}
	}

	// Un 201 sin los identificadores no sirve: sin holdId no se puede confirmar. Si el hold existe,
	// vence solo, como uno huérfano por timeout.
	private HoldResult toResult(Hold hold) {
		if (hold == null || hold.getHoldId() == null || hold.getReservationProcessId() == null
				|| hold.getExpiresAt() == null) {
			return unavailable("response without hold identifiers");
		}
		return HoldResult.created(hold);
	}

	private HoldResult unavailable(String reason) {
		log.warn("Hold request to the catedra failed ({})", reason);
		return HoldResult.failed(ReservationFailureReason.INTEGRATION_UNAVAILABLE);
	}

	// Solo el timeout de respuesta deja la duda de si el hold se creó: el pedido llegó y no se supo el
	// resultado. Un timeout de conexión es como no tener red: el pedido nunca salió.
	private static boolean isResponseTimeout(ResourceAccessException e) {
		for (Throwable cause = e; cause != null; cause = cause.getCause()) {
			if (cause instanceof HttpConnectTimeoutException) {
				return false;
			}
			if (cause instanceof HttpTimeoutException) {
				return true;
			}
		}
		return false;
	}

}
