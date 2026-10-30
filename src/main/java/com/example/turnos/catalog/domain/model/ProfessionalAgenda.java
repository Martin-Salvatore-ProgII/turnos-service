package com.example.turnos.catalog.domain.model;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * La agenda vigente de un profesional, tal como la informa el catálogo en una sola lectura
 * (ADR-0017). {@code enabled} es el habilitado efectivo (profesional y categoría, ADR-0014) y
 * {@code weeklySchedules} trae solo los horarios habilitados.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProfessionalAgenda {
	private Long id;
	private String firstName;
	private String lastName;
	private Category category;
	private boolean enabled;
	private List<WeeklySchedule> weeklySchedules;
	// Versión del catálogo con la que se respondió: queda registrada en la disponibilidad (ADR-0017).
	private Long catalogVersion;
}
