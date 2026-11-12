package com.example.turnos.reservation.infrastructure.web.mapper;

import com.example.turnos.reservation.domain.model.PageQuery;
import com.example.turnos.reservation.domain.model.ReservationOwner;
import com.example.turnos.reservation.domain.model.ReservationProcess;
import com.example.turnos.reservation.domain.model.ReservationSearchCriteria;
import com.example.turnos.reservation.domain.model.ReservationWithOwner;
import com.example.turnos.reservation.infrastructure.web.dto.AdminReservationListRequest;
import com.example.turnos.reservation.infrastructure.web.dto.AdminReservationResponse;
import com.example.turnos.reservation.infrastructure.web.dto.ReservationListRequest;
import com.example.turnos.reservation.infrastructure.web.dto.ReservationOwnerResponse;
import com.example.turnos.reservation.infrastructure.web.dto.ReservationResponse;
import com.example.turnos.shared.infrastructure.config.PaginationProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ReservationDtoMapper {

	private final PaginationProperties paginationProperties;

	// Sin page ni size, la primera página con el tamaño por defecto (contratos.md, RNF-09).
	public PageQuery toPageQuery(ReservationListRequest request) {
		if (request == null) {
			return null;
		}
		return PageQuery.builder()
				.page(request.getPage() != null ? request.getPage() : 0)
				.size(request.getSize() != null ? request.getSize() : paginationProperties.defaultSize())
				.build();
	}

	public PageQuery toPageQuery(AdminReservationListRequest request) {
		if (request == null) {
			return null;
		}
		return PageQuery.builder()
				.page(request.getPage() != null ? request.getPage() : 0)
				.size(request.getSize() != null ? request.getSize() : paginationProperties.defaultSize())
				.build();
	}

	// ownerId no viene del pedido: lo resuelve el caso de uso a partir del login.
	public ReservationSearchCriteria toCriteria(AdminReservationListRequest request) {
		if (request == null) {
			return null;
		}
		return ReservationSearchCriteria.builder()
				.ownerLogin(request.getOwnerLogin())
				.status(request.getStatus())
				.from(request.getFrom())
				.to(request.getTo())
				.build();
	}

	public AdminReservationResponse toAdminResponse(ReservationWithOwner reservation) {
		if (reservation == null) {
			return null;
		}
		ReservationProcess process = reservation.getProcess();
		ReservationOwner owner = reservation.getOwner();
		return AdminReservationResponse.builder()
				.reservation(toResponse(process))
				.owner(owner == null ? null : ReservationOwnerResponse.builder()
						.login(owner.getLogin())
						.firstName(owner.getFirstName())
						.lastName(owner.getLastName())
						.build())
				.reservationProcessId(process == null ? null : process.getReservationProcessId())
				.cancelledBy(process == null ? null : process.getCancelledBy())
				.build();
	}

	public ReservationResponse toResponse(ReservationProcess process) {
		if (process == null) {
			return null;
		}
		return ReservationResponse.builder()
				.id(process.getId())
				.status(process.getStatus() == null ? null : process.getStatus().name())
				.professionalId(process.getProfessionalId())
				.professionalFirstName(process.getProfessionalFirstName())
				.professionalLastName(process.getProfessionalLastName())
				.date(process.getAppointmentDate())
				.startTime(process.getStartTime())
				.endTime(process.getEndTime())
				.expiresAt(process.getExpiresAt())
				// En la API se llama message: es el último mensaje de la cátedra para el usuario.
				.message(process.getLastMessage())
				.failureReason(process.getFailureReason())
				.createdAt(process.getCreatedAt())
				.confirmedAt(process.getConfirmedAt())
				.cancelledAt(process.getCancelledAt())
				.cancellationReason(process.getCancellationReason())
				.build();
	}

}
