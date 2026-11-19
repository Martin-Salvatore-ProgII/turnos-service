package com.example.turnos.reservation.infrastructure.web.dto;

import java.time.LocalDate;

import com.example.turnos.reservation.domain.model.ReservationStatus;
import com.example.turnos.shared.infrastructure.web.pagination.MaxPageSize;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

// Los parámetros de GET /api/admin/reservations (docs/arq/contratos.md). Todos opcionales, con tipos
// envoltorio: un parámetro que no viene queda en null y no filtra.
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminReservationListRequest {

	// El largo máximo es el de un login (HU-01).
	@Size(min = 1, max = 50)
	@Pattern(regexp = ".*\\S.*", message = "must not be blank")
	private String ownerLogin;

	private ReservationStatus status;

	@DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
	private LocalDate from;

	@DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
	private LocalDate to;

	@Min(0)
	private Integer page;

	@Min(1)
	@MaxPageSize
	private Integer size;

	// Puede venir un solo extremo. Sin límite de "hoy" ni de horizonte (a diferencia de ADR-0065): el
	// administrador revisa también reservas pasadas.
	@AssertTrue(message = "from must not be after to")
	public boolean isDateRangeOrdered() {
		return from == null || to == null || !from.isAfter(to);
	}

}
