package com.example.turnos.catalog.infrastructure.rest.adapter;

import java.util.Optional;

import com.example.turnos.catalog.application.exception.CatalogTokenRejectedException;
import com.example.turnos.catalog.application.exception.CatalogUnavailableException;
import com.example.turnos.catalog.domain.model.ProfessionalAgenda;
import com.example.turnos.catalog.domain.ports.out.CatalogClientPort;
import com.example.turnos.catalog.infrastructure.rest.dto.ProblemDto;
import com.example.turnos.catalog.infrastructure.rest.dto.ProfessionalAgendaResponse;
import com.example.turnos.catalog.infrastructure.rest.mapper.ProfessionalAgendaMapper;
import com.example.turnos.shared.infrastructure.rest.RemoteReadRetry;
import com.example.turnos.shared.infrastructure.rest.RemoteServiceUnavailableException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
@RequiredArgsConstructor
public class CatalogServiceAdapter implements CatalogClientPort {

	static final String AGENDA_PATH = "/api/professionals/{id}/agenda";

	private static final String SERVICE_NAME = "Catalog";
	private static final String PROFESSIONAL_NOT_FOUND = "PROFESSIONAL_NOT_FOUND";

	private final RestClient catalogRestClient;
	private final RemoteReadRetry remoteReadRetry;
	private final ProfessionalAgendaMapper professionalAgendaMapper;

	// El reintento es el de toda lectura a un servicio externo (ADR-0029, ADR-0063). Si se agota, el
	// catálogo se da por no disponible y no se usa ningún dato guardado (CU-03 3c).
	@Override
	public Optional<ProfessionalAgenda> getProfessionalAgenda(Long professionalId) {
		try {
			return remoteReadRetry.read(SERVICE_NAME, () -> readAgenda(professionalId))
					.map(professionalAgendaMapper::toDomainModel);
		} catch (RemoteServiceUnavailableException e) {
			throw new CatalogUnavailableException(e.getMessage());
		}
	}

	// Un 4xx no se reintenta: repetir el pedido da la misma respuesta. Se decide por el status y el
	// code, nunca por el texto de detail (contratos.md).
	private Optional<ProfessionalAgendaResponse> readAgenda(Long professionalId) {
		try {
			ProfessionalAgendaResponse body = catalogRestClient.get()
					.uri(AGENDA_PATH, professionalId)
					.retrieve()
					.body(ProfessionalAgendaResponse.class);
			if (body == null) {
				throw new CatalogUnavailableException("Unexpected response from the catalog: empty body");
			}
			return Optional.of(body);
		} catch (HttpClientErrorException e) {
			if (e.getStatusCode().isSameCodeAs(HttpStatus.NOT_FOUND) && PROFESSIONAL_NOT_FOUND.equals(codeOf(e))) {
				return Optional.empty();
			}
			if (e.getStatusCode().isSameCodeAs(HttpStatus.UNAUTHORIZED)) {
				throw new CatalogTokenRejectedException();
			}
			throw new CatalogUnavailableException("Unexpected response from the catalog: HTTP " + e.getStatusCode().value());
		} catch (ResourceAccessException | HttpServerErrorException e) {
			// Los decide RemoteReadRetry: se reintentan.
			throw e;
		} catch (RestClientException e) {
			throw new CatalogUnavailableException("Unexpected response from the catalog: unreadable body");
		}
	}

	// Un 404 sin el código del contrato (por ejemplo, una ruta equivocada) no prueba que el
	// profesional no exista.
	private static String codeOf(HttpClientErrorException e) {
		try {
			ProblemDto problem = e.getResponseBodyAs(ProblemDto.class);
			return problem == null ? null : problem.getCode();
		} catch (RuntimeException unreadable) {
			return null;
		}
	}

}
