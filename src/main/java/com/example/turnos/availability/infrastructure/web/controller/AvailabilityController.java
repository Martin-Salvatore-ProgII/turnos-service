package com.example.turnos.availability.infrastructure.web.controller;

import com.example.turnos.availability.application.service.AvailabilityService;
import com.example.turnos.availability.domain.model.Availability;
import com.example.turnos.availability.infrastructure.web.dto.AvailabilityRequest;
import com.example.turnos.availability.infrastructure.web.dto.AvailabilityResponse;
import com.example.turnos.availability.infrastructure.web.mapper.AvailabilityDtoMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/availability")
@RequiredArgsConstructor
public class AvailabilityController {

	private final AvailabilityService availabilityService;
	private final AvailabilityDtoMapper availabilityDtoMapper;

	// Exige un JWT de usuario válido, como toda ruta que no es pública (RNF-04); es el mismo token que
	// después se reenvía al catálogo (ADR-0034, ADR-0066).
	@GetMapping
	public ResponseEntity<AvailabilityResponse> getAvailability(@Valid AvailabilityRequest request) {
		Availability availability = availabilityService.getAvailability(request.getProfessionalId(), request.getDate());
		return ResponseEntity.ok(availabilityDtoMapper.toResponse(availability));
	}

}
