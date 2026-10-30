package com.example.turnos.catalog.domain.ports.out;

import java.util.Optional;

import com.example.turnos.catalog.domain.model.ProfessionalAgenda;

public interface CatalogClientPort {

	// Pide al catálogo la agenda vigente del profesional. Vacío si el profesional no existe. Si el
	// catálogo no responde o responde algo inesperado, el adaptador lanza CatalogUnavailableException;
	// si rechaza el JWT del usuario, CatalogTokenRejectedException.
	Optional<ProfessionalAgenda> getProfessionalAgenda(Long professionalId);

}
