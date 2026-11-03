package com.example.turnos.reservation.infrastructure.rest.adapter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.example.turnos.reservation.application.exception.ReservationException;
import com.example.turnos.reservation.domain.model.CatedraAppointment;
import com.example.turnos.reservation.domain.ports.out.AppointmentPort;
import com.example.turnos.reservation.infrastructure.rest.dto.AppointmentDto;
import com.example.turnos.reservation.infrastructure.rest.dto.CancellationRequest;
import com.example.turnos.reservation.infrastructure.rest.dto.CancellationResponse;
import com.example.turnos.reservation.infrastructure.rest.mapper.AppointmentMapper;
import com.example.turnos.shared.infrastructure.rest.CatedraReadExecutor;
import com.example.turnos.shared.infrastructure.rest.CatedraRequestException;
import com.example.turnos.shared.infrastructure.rest.ProblemDto;
import com.example.turnos.shared.infrastructure.rest.RemoteCommandRetry;
import com.example.turnos.shared.infrastructure.rest.RemoteServiceUnavailableException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class CatedraAppointmentAdapter implements AppointmentPort {

	static final String APPOINTMENTS_PATH = "/api/appointments";
	static final String CANCEL_PATH = "/api/appointments/{reservationProcessId}/cancel";
	private static final String NOT_CANCELLABLE = "RESERVATION_NOT_CANCELLABLE";
	// Lo que ve el usuario cuando la cátedra no canceló. No lleva el reservationProcessId, que no se le
	// expone a la app (contratos.md), ni la URL de la cátedra: esos datos van solo al log.
	private static final String CANCELLATION_UNAVAILABLE = "The catedra could not cancel the reservation";
	static final String TOTAL_COUNT_HEADER = "X-Total-Count";
	static final int PAGE_SIZE = 100;
	// Tope de páginas por consulta: un paciente de este sistema no llega ni cerca, pero un total que la
	// cátedra informara mal no tiene que dejar el ciclo pidiendo páginas sin fin (ADR-0029, sin bucles ilimitados).
	static final int MAX_PAGES = 20;

	private static final ParameterizedTypeReference<List<AppointmentDto>> APPOINTMENTS = new ParameterizedTypeReference<>() {
	};

	private final RestClient catedraRestClient;
	private final CatedraReadExecutor catedraReadExecutor;
	private final RemoteCommandRetry remoteCommandRetry;
	private final AppointmentMapper appointmentMapper;

	// La lectura sigue las reglas de toda lectura a la cátedra (reintento y mensajes seguros, ADR-0063). La
	// cuenta técnica devuelve las reservas de todos los usuarios: el filtro por paciente va en el pedido
	// (REF §11). Se pide página por página hasta juntar el total que informa X-Total-Count.
	@Override
	public List<CatedraAppointment> findAppointments(UUID externalPatientId) {
		List<CatedraAppointment> appointments = new ArrayList<>();
		try {
			for (int page = 0; page < MAX_PAGES; page++) {
				int currentPage = page;
				ResponseEntity<List<AppointmentDto>> response = catedraReadExecutor.read(() -> catedraRestClient.get()
						.uri(uri -> uri.path(APPOINTMENTS_PATH)
								.queryParam("externalPatientId", externalPatientId)
								.queryParam("page", currentPage)
								.queryParam("size", PAGE_SIZE)
								.build())
						.retrieve()
						.toEntity(APPOINTMENTS));
				List<AppointmentDto> body = response.getBody();
				if (body == null || body.isEmpty()) {
					break;
				}
				body.stream().map(appointmentMapper::toDomainModel).forEach(appointments::add);
				if (appointments.size() >= totalOf(response, appointments.size())) {
					break;
				}
			}
		} catch (CatedraRequestException e) {
			throw new ReservationException(ReservationException.Code.INTEGRATION_UNAVAILABLE, e.getMessage());
		}
		return appointments;
	}

	// Hasta 2 reintentos con espera creciente (ADR-0029): repetirla es seguro porque la cátedra responde
	// éxito si ya estaba cancelada, así que una respuesta perdida se recupera en el reintento.
	@Override
	public Instant cancelAppointment(String reservationProcessId, String reason) {
		CancellationRequest body = appointmentMapper.toCancellationRequest(reason);
		try {
			CancellationResponse response = remoteCommandRetry.execute(
					"Cancellation of reservation process " + reservationProcessId, () -> {
						RestClient.RequestBodySpec request = catedraRestClient.post()
								.uri(CANCEL_PATH, reservationProcessId);
						if (body != null) {
							request.body(body);
						}
						return request.retrieve().body(CancellationResponse.class);
					});
			return response == null ? null : response.getCancelledAt();
		} catch (HttpClientErrorException e) {
			String code = ProblemDto.codeOf(e);
			if (NOT_CANCELLABLE.equals(code)) {
				throw new ReservationException(ReservationException.Code.RESERVATION_NOT_CANCELLABLE,
						"The reservation can not be cancelled");
			}
			// Otro 4xx (la cátedra no conoce el proceso, el JWT técnico rechazado) no es un problema del
			// pedido del usuario sino de la integración: la reserva queda como está.
			throw cancellationUnavailable(reservationProcessId, "HTTP " + e.getStatusCode().value()
					+ (code == null ? "" : " " + code));
		} catch (RemoteServiceUnavailableException e) {
			// Sin respuesta después de los reintentos la cátedra pudo haberla cancelado: si fue así, lo
			// corrigen AppointmentCancelled o la reconciliación (ADR-0026, ADR-0030).
			throw cancellationUnavailable(reservationProcessId, e.getMessage());
		} catch (RestClientException e) {
			// Un 2xx con un cuerpo que no se puede leer: tampoco se sabe con certeza qué pasó.
			throw cancellationUnavailable(reservationProcessId, "unreadable body");
		}
	}

	private static ReservationException cancellationUnavailable(String reservationProcessId, String reason) {
		log.warn("Cancellation of reservation process {} failed ({}): the reservation stays as it was",
				reservationProcessId, reason);
		return new ReservationException(ReservationException.Code.INTEGRATION_UNAVAILABLE, CANCELLATION_UNAVAILABLE);
	}

	// Sin el encabezado se toma lo recibido como el total: una sola página.
	private static long totalOf(ResponseEntity<?> response, long received) {
		String header = response.getHeaders().getFirst(TOTAL_COUNT_HEADER);
		if (header == null) {
			return received;
		}
		try {
			return Long.parseLong(header);
		} catch (NumberFormatException e) {
			return received;
		}
	}

}
