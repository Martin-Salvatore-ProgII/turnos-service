package com.example.turnos.reservation.infrastructure.web.controller;

import java.util.UUID;

import com.example.turnos.reservation.application.service.ReservationService;
import com.example.turnos.reservation.domain.model.ReservationProcess;
import com.example.turnos.reservation.infrastructure.web.dto.ReservationRequest;
import com.example.turnos.reservation.infrastructure.web.dto.ReservationResponse;
import com.example.turnos.reservation.infrastructure.web.mapper.ReservationDtoMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reservations")
@RequiredArgsConstructor
public class ReservationController {

	private final ReservationService reservationService;
	private final ReservationDtoMapper reservationDtoMapper;

	// El usuario es siempre el del JWT validado (su login, en sub), nunca uno que mande el cliente
	// (RNF-03, Constitución P-13). 201 aunque el proceso termine fallido: la reserva existe y la app la
	// sigue por su id (ADR-0069).
	@PostMapping
	public ResponseEntity<ReservationResponse> startReservation(@Valid @RequestBody ReservationRequest request,
			Authentication authentication) {
		ReservationProcess process = reservationService.startReservation(authentication.getName(),
				request.getProfessionalId(), request.getDate(), request.getStartTime());
		return new ResponseEntity<>(reservationDtoMapper.toResponse(process), HttpStatus.CREATED);
	}

	// La operación que la app consulta cada pocos segundos mientras el proceso no es final (ADR-0021).
	@GetMapping("/{id}")
	public ResponseEntity<ReservationResponse> getReservation(@PathVariable UUID id, Authentication authentication) {
		return ResponseEntity.ok(reservationDtoMapper.toResponse(
				reservationService.getReservation(authentication.getName(), id)));
	}

}
