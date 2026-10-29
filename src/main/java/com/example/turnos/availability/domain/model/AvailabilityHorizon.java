package com.example.turnos.availability.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Cuántos días hacia adelante se puede consultar la disponibilidad (ADR-0015, ADR-0065). Es un valor
// configurable (RNF-09): lo arma la configuración y lo recibe el caso de uso, que así no depende de
// infrastructure.
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AvailabilityHorizon {
	private int days;
}
