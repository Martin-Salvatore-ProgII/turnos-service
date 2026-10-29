package com.example.turnos.availability.infrastructure.rest.dto;

import java.time.LocalDate;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@JsonIgnoreProperties(ignoreUnknown = true)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OccupanciesResponse {
	private Long professionalId;
	private LocalDate from;
	private LocalDate to;
	private List<OccupancyDto> occupancies;
}
