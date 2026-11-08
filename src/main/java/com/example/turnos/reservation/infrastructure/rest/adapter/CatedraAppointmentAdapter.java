package com.example.turnos.reservation.infrastructure.rest.adapter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.example.turnos.reservation.application.exception.ReservationException;
import com.example.turnos.reservation.domain.model.CatedraAppointment;
import com.example.turnos.reservation.domain.ports.out.AppointmentPort;
import com.example.turnos.reservation.infrastructure.rest.dto.AppointmentDto;
import com.example.turnos.reservation.infrastructure.rest.mapper.AppointmentMapper;
import com.example.turnos.shared.infrastructure.rest.CatedraReadExecutor;
import com.example.turnos.shared.infrastructure.rest.CatedraRequestException;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class CatedraAppointmentAdapter implements AppointmentPort {

	static final String APPOINTMENTS_PATH = "/api/appointments";
	static final String TOTAL_COUNT_HEADER = "X-Total-Count";
	static final int PAGE_SIZE = 100;
	// Tope de páginas por consulta: un paciente de este sistema no llega ni cerca, pero un total que la
	// cátedra informara mal no tiene que dejar el ciclo pidiendo páginas sin fin (ADR-0029, sin bucles ilimitados).
	static final int MAX_PAGES = 20;

	private static final ParameterizedTypeReference<List<AppointmentDto>> APPOINTMENTS = new ParameterizedTypeReference<>() {
	};

	private final RestClient catedraRestClient;
	private final CatedraReadExecutor catedraReadExecutor;
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
