package com.example.turnos.availability.infrastructure.rest.adapter;

import java.time.LocalDate;
import java.util.List;

import com.example.turnos.availability.application.exception.IntegrationUnavailableException;
import com.example.turnos.availability.domain.model.Occupancy;
import com.example.turnos.availability.domain.ports.out.OccupancyPort;
import com.example.turnos.availability.infrastructure.rest.dto.OccupanciesResponse;
import com.example.turnos.availability.infrastructure.rest.mapper.OccupancyMapper;
import com.example.turnos.shared.infrastructure.rest.CatedraReadExecutor;
import com.example.turnos.shared.infrastructure.rest.CatedraRequestException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@RequiredArgsConstructor
public class CatedraOccupancyAdapter implements OccupancyPort {

	static final String OCCUPANCIES_PATH = "/api/appointment-occupancies";

	private final RestClient catedraRestClient;
	private final CatedraReadExecutor catedraReadExecutor;
	private final OccupancyMapper occupancyMapper;

	// El reintento y los mensajes de falla son los de toda lectura a la cátedra (ADR-0029, ADR-0063).
	// Acá solo se pasan a la excepción de la feature. Un 400 de la cátedra también termina en 503: la
	// fecha ya se validó antes, así que sería un error nuestro y no del usuario.
	@Override
	public List<Occupancy> getOccupancies(Long professionalId, LocalDate date) {
		OccupanciesResponse response;
		try {
			response = catedraReadExecutor.read(() -> catedraRestClient.get()
					.uri(uri -> uri.path(OCCUPANCIES_PATH)
							.queryParam("professionalId", professionalId)
							// Un solo día por consulta (ADR-0015).
							.queryParam("from", date)
							.queryParam("to", date)
							.build())
					.retrieve()
					.body(OccupanciesResponse.class));
		} catch (CatedraRequestException e) {
			throw new IntegrationUnavailableException(e.getMessage());
		}
		// Sin la lista no se sabe qué está ocupado, y ofrecer slots sin descontarlos sería mostrar
		// como libre algo que puede no estarlo (CU-03 4a). Vacía sí es válida (REF §8).
		if (response.getOccupancies() == null) {
			throw new IntegrationUnavailableException("Unexpected response from the catedra: no occupancies");
		}
		return response.getOccupancies().stream().map(occupancyMapper::toDomainModel).toList();
	}

}
