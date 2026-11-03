package com.example.turnos.reservation.infrastructure.web.dto;

import com.example.turnos.reservation.domain.model.ReservationStatus;
import com.example.turnos.shared.infrastructure.web.pagination.MaxPageSize;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Los parámetros de GET /api/reservations (docs/arq/contratos.md). Todos opcionales, por eso con tipos
// envoltorio. Un status que no es un estado de la máquina no se convierte y es un 400 VALIDATION_ERROR.
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReservationListRequest {

	private ReservationStatus status;

	@Min(0)
	private Integer page;

	@Min(1)
	@MaxPageSize
	private Integer size;

}
