package com.example.turnos.reservation.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.example.turnos.reservation.application.exception.ReservationException;
import com.example.turnos.reservation.domain.model.PageQuery;
import com.example.turnos.reservation.domain.model.PageResult;
import com.example.turnos.reservation.domain.model.ReservationProcess;
import com.example.turnos.reservation.domain.model.ReservationSearchCriteria;
import com.example.turnos.reservation.domain.model.ReservationStatus;
import com.example.turnos.reservation.domain.model.ReservationWithOwner;
import com.example.turnos.reservation.domain.ports.in.CancelAnyReservationUseCase;
import com.example.turnos.reservation.domain.ports.in.CancelReservationUseCase;
import com.example.turnos.reservation.domain.ports.in.GetAnyReservationUseCase;
import com.example.turnos.reservation.domain.ports.in.GetReservationUseCase;
import com.example.turnos.reservation.domain.ports.in.HandleReservationEventUseCase;
import com.example.turnos.reservation.domain.ports.in.ListAllReservationsUseCase;
import com.example.turnos.reservation.domain.ports.in.ListReservationsUseCase;
import com.example.turnos.reservation.domain.ports.in.PurgeProcessedEventsUseCase;
import com.example.turnos.reservation.domain.ports.in.ReconcileReservationProcessesUseCase;
import com.example.turnos.reservation.domain.ports.in.StartReservationUseCase;
import com.example.turnos.reservation.domain.ports.in.SubmitPhoneUseCase;
import org.junit.jupiter.api.Test;

// La fachada: delega en los puertos de entrada y traduce el vacío en RESERVATION_NOT_FOUND (ADR-0059).
class ReservationServiceTest {

	private static final UUID ID = UUID.randomUUID();

	private final StartReservationUseCase startReservationUseCase = mock(StartReservationUseCase.class);
	private final GetReservationUseCase getReservationUseCase = mock(GetReservationUseCase.class);
	private final ListReservationsUseCase listReservationsUseCase = mock(ListReservationsUseCase.class);
	private final HandleReservationEventUseCase handleReservationEventUseCase = mock(HandleReservationEventUseCase.class);
	private final SubmitPhoneUseCase submitPhoneUseCase = mock(SubmitPhoneUseCase.class);
	private final CancelReservationUseCase cancelReservationUseCase = mock(CancelReservationUseCase.class);
	private final ReconcileReservationProcessesUseCase reconcileUseCase = mock(ReconcileReservationProcessesUseCase.class);
	private final PurgeProcessedEventsUseCase purgeUseCase = mock(PurgeProcessedEventsUseCase.class);
	private final ListAllReservationsUseCase listAllReservationsUseCase = mock(ListAllReservationsUseCase.class);
	private final GetAnyReservationUseCase getAnyReservationUseCase = mock(GetAnyReservationUseCase.class);
	private final CancelAnyReservationUseCase cancelAnyReservationUseCase = mock(CancelAnyReservationUseCase.class);
	private final ReservationService service = new ReservationService(startReservationUseCase, getReservationUseCase,
			listReservationsUseCase, listAllReservationsUseCase, getAnyReservationUseCase, handleReservationEventUseCase,
			submitPhoneUseCase, cancelReservationUseCase, cancelAnyReservationUseCase, reconcileUseCase, purgeUseCase);

	@Test
	void startReservationDelegatesToItsUseCase() {
		ReservationProcess process = ReservationProcess.builder().id(ID).build();
		LocalDate date = LocalDate.parse("2026-11-09");
		LocalTime start = LocalTime.parse("11:20");
		when(startReservationUseCase.startReservation("juan", 204L, date, start)).thenReturn(process);

		assertThat(service.startReservation("juan", 204L, date, start)).isSameAs(process);
	}

	@Test
	void getReservationReturnsTheReservationOfTheUser() {
		ReservationProcess process = ReservationProcess.builder().id(ID).build();
		when(getReservationUseCase.getReservation("juan", ID)).thenReturn(Optional.of(process));

		assertThat(service.getReservation("juan", ID)).isSameAs(process);
	}

	@Test
	void aMissingReservationIsReservationNotFound() {
		when(getReservationUseCase.getReservation("juan", ID)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.getReservation("juan", ID))
				.isInstanceOf(ReservationException.class)
				.extracting("code").isEqualTo(ReservationException.Code.RESERVATION_NOT_FOUND);
	}

	@Test
	void listReservationsReturnsThePageOfItsUseCase() {
		PageQuery page = PageQuery.builder().page(0).size(20).build();
		PageResult<ReservationProcess> result = PageResult.<ReservationProcess>builder().content(List.of()).build();
		when(listReservationsUseCase.listReservations("juan", ReservationStatus.CONFIRMED, page)).thenReturn(result);

		assertThat(service.listReservations("juan", ReservationStatus.CONFIRMED, page)).isSameAs(result);
	}

	@Test
	void getAnyReservationReturnsTheReservationOfItsUseCase() {
		ReservationWithOwner reservation = ReservationWithOwner.builder().build();
		when(getAnyReservationUseCase.getAnyReservation(ID)).thenReturn(Optional.of(reservation));

		assertThat(service.getAnyReservation(ID)).isSameAs(reservation);
	}

	@Test
	void aMissingReservationForTheAdministratorIsReservationNotFound() {
		when(getAnyReservationUseCase.getAnyReservation(ID)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.getAnyReservation(ID))
				.isInstanceOf(ReservationException.class)
				.extracting("code").isEqualTo(ReservationException.Code.RESERVATION_NOT_FOUND);
	}

	@Test
	void cancelAnyReservationReturnsTheReservationOfItsUseCase() {
		ReservationWithOwner reservation = ReservationWithOwner.builder().build();
		when(cancelAnyReservationUseCase.cancelAnyReservation("admin", ID, "Pedido por soporte")).thenReturn(reservation);

		assertThat(service.cancelAnyReservation("admin", ID, "Pedido por soporte")).isSameAs(reservation);
	}

	@Test
	void listAllReservationsReturnsThePageOfItsUseCase() {
		ReservationSearchCriteria criteria = ReservationSearchCriteria.builder().ownerLogin("juan").build();
		PageQuery page = PageQuery.builder().page(0).size(20).build();
		PageResult<ReservationWithOwner> result = PageResult.<ReservationWithOwner>builder().content(List.of()).build();
		when(listAllReservationsUseCase.listAllReservations(criteria, page)).thenReturn(result);

		assertThat(service.listAllReservations(criteria, page)).isSameAs(result);
	}

	@Test
	void handleReservationEventDelegatesToItsUseCaseAndReturnsItsResult() {
		com.example.turnos.reservation.domain.model.ReservationEvent event = com.example.turnos.reservation.domain.model.ReservationEvent
				.builder().eventId("e-1").build();
		when(handleReservationEventUseCase.handleReservationEvent(event))
				.thenReturn(com.example.turnos.reservation.domain.model.EventHandlingResult.DUPLICATE);

		assertThat(service.handleReservationEvent(event))
				.isEqualTo(com.example.turnos.reservation.domain.model.EventHandlingResult.DUPLICATE);
	}

	@Test
	void submitPhoneDelegatesToItsUseCase() {
		ReservationProcess process = ReservationProcess.builder().id(ID).build();
		when(submitPhoneUseCase.submitPhone("juan", ID, "+5492615551234")).thenReturn(process);

		assertThat(service.submitPhone("juan", ID, "+5492615551234")).isSameAs(process);
	}

	@Test
	void cancelReservationDelegatesToItsUseCase() {
		ReservationProcess process = ReservationProcess.builder().id(ID).build();
		when(cancelReservationUseCase.cancelReservation("juan", ID, "No puedo ir")).thenReturn(process);

		assertThat(service.cancelReservation("juan", ID, "No puedo ir")).isSameAs(process);
	}

	@Test
	void reconciliationAndPurgeDelegateToTheirUseCases() {
		when(reconcileUseCase.reconcileReservationProcesses(java.time.Duration.ofSeconds(30))).thenReturn(2);
		when(purgeUseCase.purgeProcessedEvents(java.time.Duration.ofDays(7))).thenReturn(5);

		assertThat(service.reconcileReservationProcesses(java.time.Duration.ofSeconds(30))).isEqualTo(2);
		assertThat(service.purgeProcessedEvents(java.time.Duration.ofDays(7))).isEqualTo(5);
	}

}
