package com.example.turnos.reservation.infrastructure.persistence.repository;

import java.time.LocalDate;

import com.example.turnos.reservation.domain.model.ReservationStatus;
import com.example.turnos.reservation.infrastructure.persistence.entity.ReservationProcessEntity;
import org.springframework.data.jpa.domain.Specification;

/**
 * Los filtros del listado administrativo de reservas, uno por método. Cada uno es una condición de la
 * consulta, y el adaptador combina con {@code and} solo los que se pidieron. Con cuatro filtros
 * opcionales, un método derivado por combinación serían dieciséis.
 */
public final class ReservationProcessSpecifications {

	private ReservationProcessSpecifications() {
	}

	public static Specification<ReservationProcessEntity> hasOwner(Long userId) {
		return (root, query, cb) -> cb.equal(root.get("userId"), userId);
	}

	public static Specification<ReservationProcessEntity> hasStatus(ReservationStatus status) {
		return (root, query, cb) -> cb.equal(root.get("status"), status);
	}

	// Los dos extremos del rango van incluidos: "desde el 10" incluye los turnos del 10.
	public static Specification<ReservationProcessEntity> appointmentFrom(LocalDate from) {
		return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("appointmentDate"), from);
	}

	public static Specification<ReservationProcessEntity> appointmentTo(LocalDate to) {
		return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("appointmentDate"), to);
	}

}
