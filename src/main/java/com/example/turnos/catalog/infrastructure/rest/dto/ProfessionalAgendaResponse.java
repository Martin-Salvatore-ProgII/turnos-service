package com.example.turnos.catalog.infrastructure.rest.dto;

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
public class ProfessionalAgendaResponse {
	private Long id;
	private String firstName;
	private String lastName;
	private CategoryDto category;
	private Boolean enabled;
	private List<WeeklyScheduleDto> weeklySchedules;
	private Long catalogVersion;
}
