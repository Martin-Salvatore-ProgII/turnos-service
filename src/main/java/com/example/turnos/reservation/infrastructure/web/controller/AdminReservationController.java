package com.example.turnos.reservation.infrastructure.web.controller;

import java.util.List;
import java.util.UUID;

import com.example.turnos.reservation.application.service.ReservationService;
import com.example.turnos.reservation.domain.model.PageResult;
import com.example.turnos.reservation.domain.model.ReservationWithOwner;
import com.example.turnos.reservation.infrastructure.web.dto.AdminReservationListRequest;
import com.example.turnos.reservation.infrastructure.web.dto.AdminReservationResponse;
import com.example.turnos.reservation.infrastructure.web.mapper.ReservationDtoMapper;
import com.example.turnos.shared.infrastructure.web.pagination.PaginationHeaders;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Las operaciones del administrador sobre reservas (HU-10, ADR-0037). Es un controller aparte, en su propia
// ruta, porque el acceso se decide por ruta: SecurityConfig exige ROLE_ADMIN en /api/admin/**. Ni este
// controller ni el de las rutas de usuario miran el rol.
@RestController
@RequestMapping("/api/admin/reservations")
@RequiredArgsConstructor
public class AdminReservationController {

	private final ReservationService reservationService;
	private final ReservationDtoMapper reservationDtoMapper;

	// Como la lista de las reservas propias: el total y los enlaces a las páginas vecinas van en las cabeceras.
	@GetMapping
	public ResponseEntity<List<AdminReservationResponse>> listAllReservations(
			@Valid AdminReservationListRequest request) {
		PageResult<ReservationWithOwner> result = reservationService.listAllReservations(
				reservationDtoMapper.toCriteria(request), reservationDtoMapper.toPageQuery(request));
		return ResponseEntity.ok()
				.headers(PaginationHeaders.of(result.getTotalElements(), result.getPage(), result.getSize()))
				.body(result.getContent().stream().map(reservationDtoMapper::toAdminResponse).toList());
	}

	// La de cualquier usuario; una inexistente es 404 RESERVATION_NOT_FOUND.
	@GetMapping("/{id}")
	public ResponseEntity<AdminReservationResponse> getAnyReservation(@PathVariable UUID id) {
		return ResponseEntity.ok(reservationDtoMapper.toAdminResponse(reservationService.getAnyReservation(id)));
	}

}
