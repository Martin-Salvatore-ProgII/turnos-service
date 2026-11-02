package com.example.turnos.reservation.domain.model;

import static com.example.turnos.reservation.domain.model.ReservationStatus.AWAITING_PHONE;
import static com.example.turnos.reservation.domain.model.ReservationStatus.AWAITING_REQUEST;
import static com.example.turnos.reservation.domain.model.ReservationStatus.CANCELLED;
import static com.example.turnos.reservation.domain.model.ReservationStatus.CONFIRMED;
import static com.example.turnos.reservation.domain.model.ReservationStatus.EXPIRED;
import static com.example.turnos.reservation.domain.model.ReservationStatus.FAILED;
import static com.example.turnos.reservation.domain.model.ReservationStatus.HELD;
import static com.example.turnos.reservation.domain.model.ReservationStatus.INVALID;
import static com.example.turnos.reservation.domain.model.ReservationStatus.PHONE_SUBMITTED;
import static com.example.turnos.reservation.domain.model.ReservationStatus.STARTED;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * La máquina de estados de docs/arq/maquina-estados.md, probada en todas las combinaciones de estado
 * y disparador. La tabla esperada se escribe acá a mano, copiando la del documento, y no se deriva
 * del código: si alguien cambia una transición en el código sin cambiar el documento, este test falla.
 */
class ReservationProcessTest {

	private static final List<ReservationStatus> NON_FINAL =
			List.of(STARTED, HELD, AWAITING_REQUEST, AWAITING_PHONE, PHONE_SUBMITTED);

	// Disparador → (estado de origen → estado de destino). Lo que no está acá se ignora.
	private static final Map<ReservationTrigger, Map<ReservationStatus, ReservationStatus>> TRANSITIONS = Map.ofEntries(
			Map.entry(ReservationTrigger.HOLD_CREATED, Map.of(STARTED, HELD)),
			Map.entry(ReservationTrigger.HOLD_FAILED, Map.of(STARTED, FAILED)),
			Map.entry(ReservationTrigger.CONFIRMATION_ACCEPTED, Map.of(HELD, AWAITING_REQUEST)),
			Map.entry(ReservationTrigger.CONFIRMATION_EXPIRED, Map.of(HELD, EXPIRED)),
			Map.entry(ReservationTrigger.CONFIRMATION_REJECTED, Map.of(HELD, FAILED)),
			Map.entry(ReservationTrigger.INFORMATION_REQUESTED, Map.of(HELD, AWAITING_PHONE, AWAITING_REQUEST, AWAITING_PHONE)),
			Map.entry(ReservationTrigger.INFORMATION_SUBMITTED, Map.of(AWAITING_PHONE, PHONE_SUBMITTED)),
			Map.entry(ReservationTrigger.INFORMATION_REJECTED, Map.of(PHONE_SUBMITTED, AWAITING_PHONE)),
			Map.entry(ReservationTrigger.APPOINTMENT_CONFIRMED, fromEveryNonFinal(CONFIRMED)),
			Map.entry(ReservationTrigger.PROCESS_EXPIRED, fromEveryNonFinal(EXPIRED)),
			Map.entry(ReservationTrigger.PROCESS_INVALID, fromEveryNonFinal(INVALID)),
			Map.entry(ReservationTrigger.APPOINTMENT_CANCELLED, Map.of(CONFIRMED, CANCELLED)),
			Map.entry(ReservationTrigger.EXPIRED_WITHOUT_PHONE,
					Map.of(HELD, EXPIRED, AWAITING_REQUEST, EXPIRED, AWAITING_PHONE, EXPIRED)),
			Map.entry(ReservationTrigger.CATEDRA_REPORTED_FAILED, Map.of(PHONE_SUBMITTED, EXPIRED)));

	@ParameterizedTest(name = "{0} + {1}")
	@MethodSource("everyStatusAndTrigger")
	void appliesOnlyTheTransitionsOfTheStateMachine(ReservationStatus from, ReservationTrigger trigger) {
		ReservationProcess process = ReservationProcess.builder().status(from).build();
		ReservationStatus expected = TRANSITIONS.get(trigger).get(from);

		boolean applied = process.apply(trigger);

		if (expected == null) {
			// Sin transición: se ignora y el estado no cambia (ADR-0023).
			assertThat(applied).isFalse();
			assertThat(process.getStatus()).isEqualTo(from);
		} else {
			assertThat(applied).isTrue();
			assertThat(process.getStatus()).isEqualTo(expected);
		}
	}

	@ParameterizedTest(name = "{0} + {1}")
	@MethodSource("everyStatusAndTrigger")
	void aFinalStatusIsOnlyLeftFromConfirmedToCancelled(ReservationStatus from, ReservationTrigger trigger) {
		ReservationProcess process = ReservationProcess.builder().status(from).build();

		process.apply(trigger);

		if (from.isFinal() && process.getStatus() != from) {
			assertThat(from).isEqualTo(CONFIRMED);
			assertThat(process.getStatus()).isEqualTo(CANCELLED);
		}
	}

	@Test
	void aRepeatedTriggerDoesNotChangeTheResult() {
		ReservationProcess process = ReservationProcess.builder().status(PHONE_SUBMITTED).build();

		assertThat(process.apply(ReservationTrigger.APPOINTMENT_CONFIRMED)).isTrue();
		assertThat(process.apply(ReservationTrigger.APPOINTMENT_CONFIRMED)).isFalse();
		assertThat(process.apply(ReservationTrigger.PROCESS_EXPIRED)).isFalse();
		assertThat(process.getStatus()).isEqualTo(CONFIRMED);
	}

	@Test
	void theConfirmationResponseArrivingAfterThePhoneRequestDoesNotGoBack() {
		// CU-04, 8a: el pedido de teléfono llega antes que el 202 de la confirmación.
		ReservationProcess process = ReservationProcess.builder().status(HELD).build();

		process.apply(ReservationTrigger.INFORMATION_REQUESTED);
		boolean applied = process.apply(ReservationTrigger.CONFIRMATION_ACCEPTED);

		assertThat(applied).isFalse();
		assertThat(process.getStatus()).isEqualTo(AWAITING_PHONE);
	}

	@Test
	void activeStatusesAreTheNonFinalOnes() {
		assertThat(ReservationStatus.active()).containsExactlyInAnyOrderElementsOf(NON_FINAL);
	}

	private static Stream<Arguments> everyStatusAndTrigger() {
		List<Arguments> combinations = new ArrayList<>();
		for (ReservationStatus status : ReservationStatus.values()) {
			for (ReservationTrigger trigger : ReservationTrigger.values()) {
				combinations.add(Arguments.of(status, trigger));
			}
		}
		return combinations.stream();
	}

	private static Map<ReservationStatus, ReservationStatus> fromEveryNonFinal(ReservationStatus target) {
		return Map.of(STARTED, target, HELD, target, AWAITING_REQUEST, target, AWAITING_PHONE, target,
				PHONE_SUBMITTED, target);
	}

}
