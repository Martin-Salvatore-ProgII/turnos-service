package com.example.turnos.availability.domain.model;

import java.time.LocalDate;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Los slots libres de un profesional en una fecha (CU-03), con sus datos y la versión del catálogo
 * con la que se calcularon. No se guarda: es una foto del momento (ADR-0015).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Availability {
	private Long professionalId;
	private String professionalFirstName;
	private String professionalLastName;
	private LocalDate date;
	private Long catalogVersion;
	private List<Slot> slots;
}
