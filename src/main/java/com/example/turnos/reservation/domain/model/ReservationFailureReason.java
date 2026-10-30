package com.example.turnos.reservation.domain.model;

/**
 * Motivos de FAILED propios, para cuando no hubo respuesta de la cátedra. Cuando la cátedra rechaza,
 * el motivo es su propio code (contratos.md). Son códigos estables: la app muestra un mensaje propio
 * para cada uno, nunca el código ni un texto técnico (arq/interfaz.md).
 */
public final class ReservationFailureReason {

	// El pedido salió y no hubo respuesta a tiempo: el hold pudo crearse y quedar huérfano (ADR-0028).
	public static final String INTEGRATION_TIMEOUT = "INTEGRATION_TIMEOUT";
	// Sin conexión, error del lado de la cátedra o una respuesta que no es del contrato (ADR-0069).
	public static final String INTEGRATION_UNAVAILABLE = "INTEGRATION_UNAVAILABLE";

	private ReservationFailureReason() {
	}

}
