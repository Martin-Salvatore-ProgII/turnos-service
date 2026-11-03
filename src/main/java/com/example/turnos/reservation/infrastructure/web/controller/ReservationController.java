package com.example.turnos.reservation.infrastructure.web.controller;

import java.util.List;
import java.util.UUID;

import com.example.turnos.reservation.application.service.ReservationService;
import com.example.turnos.reservation.domain.model.PageResult;
import com.example.turnos.reservation.domain.model.ReservationProcess;
import com.example.turnos.reservation.infrastructure.web.dto.PhoneRequest;
import com.example.turnos.reservation.infrastructure.web.dto.ReservationListRequest;
import com.example.turnos.reservation.infrastructure.web.dto.ReservationRequest;
import com.example.turnos.reservation.infrastructure.web.dto.ReservationResponse;
import com.example.turnos.reservation.infrastructure.web.mapper.ReservationDtoMapper;
import com.example.turnos.shared.infrastructure.web.pagination.PaginationHeaders;
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

	// Las reservas del usuario del token, nunca las de otro: el usuario no es un parámetro (HU-08, RNF-03).
	// El total y los enlaces a las páginas vecinas van en las cabeceras, como en el resto de las listas.
	@GetMapping
	public ResponseEntity<List<ReservationResponse>> listReservations(@Valid ReservationListRequest request,
			Authentication authentication) {
		PageResult<ReservationProcess> result = reservationService.listReservations(authentication.getName(),
				request.getStatus(), reservationDtoMapper.toPageQuery(request));
		return ResponseEntity.ok()
				.headers(PaginationHeaders.of(result.getTotalElements(), result.getPage(), result.getSize()))
				.body(result.getContent().stream().map(reservationDtoMapper::toResponse).toList());
	}

	// La operación que la app consulta cada pocos segundos mientras el proceso no es final (ADR-0021).
	@GetMapping("/{id}")
	public ResponseEntity<ReservationResponse> getReservation(@PathVariable UUID id, Authentication authentication) {
		return ResponseEntity.ok(reservationDtoMapper.toResponse(
				reservationService.getReservation(authentication.getName(), id)));
	}

	// 202 y no 200: el teléfono se publicó y la cátedra todavía tiene que resolverlo; el resultado llega
	// por Kafka y la app lo ve consultando la reserva (ADR-0021). Responde la reserva en PHONE_SUBMITTED.
	@PostMapping("/{id}/phone")
	public ResponseEntity<ReservationResponse> submitPhone(@PathVariable UUID id,
			@Valid @RequestBody PhoneRequest request, Authentication authentication) {
		ReservationProcess process = reservationService.submitPhone(authentication.getName(), id,
				request.getPhoneNumber());
		return new ResponseEntity<>(reservationDtoMapper.toResponse(process), HttpStatus.ACCEPTED);
	}

}
