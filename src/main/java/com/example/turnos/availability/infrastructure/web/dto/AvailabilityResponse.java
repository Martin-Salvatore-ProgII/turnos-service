package com.example.turnos.availability.infrastructure.web.dto;

import java.time.LocalDate;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AvailabilityResponse {
	private Long professionalId;
	private String professionalFirstName;
	private String professionalLastName;
	private LocalDate date;
	private Long catalogVersion;
	private List<SlotResponse> slots;
}
