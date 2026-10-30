package com.example.turnos.reservation.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.example.turnos.availability.domain.model.AvailabilityHorizon;
import com.example.turnos.catalog.application.exception.CatalogUnavailableException;
import com.example.turnos.catalog.domain.model.ProfessionalAgenda;
import com.example.turnos.catalog.domain.model.WeeklySchedule;
import com.example.turnos.catalog.domain.ports.out.CatalogClientPort;
import com.example.turnos.reservation.application.exception.ReservationException;
import com.example.turnos.reservation.domain.model.ConfirmationResult;
import com.example.turnos.reservation.domain.model.Hold;
import com.example.turnos.reservation.domain.model.HoldResult;
import com.example.turnos.reservation.domain.model.ReservationFailureReason;
import com.example.turnos.reservation.domain.model.ReservationProcess;
import com.example.turnos.reservation.domain.model.ReservationStatus;
import com.example.turnos.reservation.domain.ports.out.HoldPort;
import com.example.turnos.reservation.domain.ports.out.ReservationProcessRepository;
import com.example.turnos.user.domain.model.User;
import com.example.turnos.user.domain.ports.out.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

// Las reglas de CU-04, pasos 3 a 6, con JUnit y Mockito, sin Spring. La agenda es la de Laura Medina
// (204): lunes de 11:00 a 15:00, slots de 20 minutos. "Ahora" es el lunes 2026-11-02 a las 12:30 de
// Argentina (15:30 UTC).
class StartReservationUseCaseImplTest {

	private static final Instant NOW = Instant.parse("2026-11-02T15:30:00Z");
	private static final LocalDate TODAY = LocalDate.parse("2026-11-02");
	private static final LocalDate NEXT_MONDAY = LocalDate.parse("2026-11-09");
	private static final LocalTime ELEVEN_TWENTY = LocalTime.parse("11:20");
	private static final long LAURA = 204L;
	private static final UUID EXTERNAL_PATIENT_ID = UUID.fromString("0f8fad5b-d9cb-469f-a165-70867728950e");

	private final UserRepository userRepository = mock(UserRepository.class);
	private final CatalogClientPort catalogClientPort = mock(CatalogClientPort.class);
	private final ReservationProcessRepository reservationProcessRepository = mock(ReservationProcessRepository.class);
	private final HoldPort holdPort = mock(HoldPort.class);

	// El estado con el que se guardó el proceso en cada llamada a la base, en orden.
	private final List<ReservationStatus> savedStatuses = new ArrayList<>();

	private StartReservationUseCaseImpl useCase;

	@BeforeEach
	void setUp() {
		useCase = new StartReservationUseCaseImpl(userRepository, catalogClientPort, reservationProcessRepository,
				holdPort, AvailabilityHorizon.builder().days(60).build(), Clock.fixed(NOW, ZoneOffset.UTC));
		when(userRepository.findByLogin("juan")).thenReturn(Optional.of(juan()));
		when(catalogClientPort.getProfessionalAgenda(LAURA)).thenReturn(Optional.of(laura(true)));
		when(reservationProcessRepository.create(any())).thenAnswer(invocation -> saved(invocation.getArgument(0)));
		when(reservationProcessRepository.update(any(), any()))
				.thenAnswer(invocation -> Optional.of(saved(invocation.getArgument(1))));
		when(holdPort.confirmHold(any(), any(), any(), any())).thenReturn(ConfirmationResult.accepted(null));
	}

	@Test
	void savesEachStepBeforeTheNextCallAndEndsAwaitingTheRequest() {
		Instant holdExpiresAt = Instant.parse("2026-11-02T15:40:00Z");
		Instant processExpiresAt = Instant.parse("2026-11-02T15:41:00Z");
		when(holdPort.createHold(LAURA, NEXT_MONDAY, ELEVEN_TWENTY, EXTERNAL_PATIENT_ID))
				.thenReturn(HoldResult.created(new Hold("hold-1", "process-1", holdExpiresAt)));
		when(holdPort.confirmHold("hold-1", EXTERNAL_PATIENT_ID, "Juan", "Perez"))
				.thenReturn(ConfirmationResult.accepted(processExpiresAt));

		ReservationProcess process = useCase.startReservation("juan", LAURA, NEXT_MONDAY, ELEVEN_TWENTY);

		// ADR-0022: guardado en STARTED antes del hold, y en HELD (con sus identificadores) antes de
		// confirmar. Así un evento que llegue antes que la respuesta encuentra el proceso.
		InOrder order = inOrder(reservationProcessRepository, holdPort);
		order.verify(reservationProcessRepository).create(any());
		order.verify(holdPort).createHold(LAURA, NEXT_MONDAY, ELEVEN_TWENTY, EXTERNAL_PATIENT_ID);
		order.verify(reservationProcessRepository).update(eq(process.getId()), any());
		order.verify(holdPort).confirmHold("hold-1", EXTERNAL_PATIENT_ID, "Juan", "Perez");
		order.verify(reservationProcessRepository).update(eq(process.getId()), any());
		assertThat(savedStatuses).containsExactly(ReservationStatus.STARTED, ReservationStatus.HELD,
				ReservationStatus.AWAITING_REQUEST);

		assertThat(process.getStatus()).isEqualTo(ReservationStatus.AWAITING_REQUEST);
		assertThat(process.getHoldId()).isEqualTo("hold-1");
		assertThat(process.getReservationProcessId()).isEqualTo("process-1");
		// El vencimiento es el que informó la confirmación.
		assertThat(process.getExpiresAt()).isEqualTo(processExpiresAt);
		assertThat(process.getFailureReason()).isNull();
	}

	@Test
	void anAlreadyConfirmedHoldKeepsTheExpirationOfTheHold() {
		// Un reintento cuya respuesta fue RESERVATION_PROCESS_ALREADY_CONFIRMED: es un éxito sin vencimiento.
		Instant holdExpiresAt = Instant.parse("2026-11-02T15:40:00Z");
		when(holdPort.createHold(any(), any(), any(), any()))
				.thenReturn(HoldResult.created(new Hold("hold-1", "process-1", holdExpiresAt)));

		ReservationProcess process = useCase.startReservation("juan", LAURA, NEXT_MONDAY, ELEVEN_TWENTY);

		assertThat(process.getStatus()).isEqualTo(ReservationStatus.AWAITING_REQUEST);
		assertThat(process.getExpiresAt()).isEqualTo(holdExpiresAt);
	}

	@Test
	void anExpiredHoldEndsTheProcessAsExpired() {
		whenTheHoldIsCreated();
		when(holdPort.confirmHold(any(), any(), any(), any())).thenReturn(ConfirmationResult.expired("HOLD_EXPIRED"));

		ReservationProcess process = useCase.startReservation("juan", LAURA, NEXT_MONDAY, ELEVEN_TWENTY);

		assertThat(process.getStatus()).isEqualTo(ReservationStatus.EXPIRED);
		assertThat(process.getFailureReason()).isEqualTo("HOLD_EXPIRED");
	}

	@Test
	void aRejectedConfirmationEndsTheProcessAsFailedWithTheReason() {
		whenTheHoldIsCreated();
		when(holdPort.confirmHold(any(), any(), any(), any()))
				.thenReturn(ConfirmationResult.rejected("SLOT_ALREADY_RESERVED"));

		ReservationProcess process = useCase.startReservation("juan", LAURA, NEXT_MONDAY, ELEVEN_TWENTY);

		assertThat(savedStatuses).containsExactly(ReservationStatus.STARTED, ReservationStatus.HELD,
				ReservationStatus.FAILED);
		assertThat(process.getFailureReason()).isEqualTo("SLOT_ALREADY_RESERVED");
	}

	@Test
	void aConfirmationWithoutResponseLeavesTheProcessHeld() {
		// ADR-0029: la cátedra pudo haberla recibido, así que no se cierra ni se vuelve a guardar.
		whenTheHoldIsCreated();
		when(holdPort.confirmHold(any(), any(), any(), any())).thenReturn(ConfirmationResult.noResponse());

		ReservationProcess process = useCase.startReservation("juan", LAURA, NEXT_MONDAY, ELEVEN_TWENTY);

		assertThat(process.getStatus()).isEqualTo(ReservationStatus.HELD);
		assertThat(process.getFailureReason()).isNull();
		assertThat(savedStatuses).containsExactly(ReservationStatus.STARTED, ReservationStatus.HELD);
	}

	@Test
	void keepsTheOwnerAndTheHistoricalDataOfTheSlot() {
		when(holdPort.createHold(any(), any(), any(), any()))
				.thenReturn(HoldResult.created(new Hold("hold-1", "process-1", NOW)));

		ReservationProcess process = useCase.startReservation("juan", LAURA, NEXT_MONDAY, ELEVEN_TWENTY);

		assertThat(process.getId()).isNotNull();
		assertThat(process.getUserId()).isEqualTo(7L);
		assertThat(process.getProfessionalId()).isEqualTo(LAURA);
		assertThat(process.getProfessionalFirstName()).isEqualTo("Laura");
		assertThat(process.getProfessionalLastName()).isEqualTo("Medina");
		assertThat(process.getAppointmentDate()).isEqualTo(NEXT_MONDAY);
		assertThat(process.getStartTime()).isEqualTo(ELEVEN_TWENTY);
		// El fin sale del slot de la agenda, no del pedido.
		assertThat(process.getEndTime()).isEqualTo(LocalTime.parse("11:40"));
	}

	@Test
	void aRejectedHoldEndsTheProcessAsFailedWithTheCodeOfTheCatedra() {
		when(holdPort.createHold(any(), any(), any(), any())).thenReturn(HoldResult.failed("SLOT_ALREADY_HELD"));

		ReservationProcess process = useCase.startReservation("juan", LAURA, NEXT_MONDAY, ELEVEN_TWENTY);

		assertThat(savedStatuses).containsExactly(ReservationStatus.STARTED, ReservationStatus.FAILED);
		assertThat(process.getStatus()).isEqualTo(ReservationStatus.FAILED);
		assertThat(process.getFailureReason()).isEqualTo("SLOT_ALREADY_HELD");
		assertThat(process.getHoldId()).isNull();
		// Sin hold no hay nada que confirmar.
		verify(holdPort, never()).confirmHold(any(), any(), any(), any());
	}

	@Test
	void aHoldWithoutResponseEndsTheProcessAsFailedWithTheTimeout() {
		// ADR-0028: no se reintenta; el usuario puede volver a intentar.
		when(holdPort.createHold(any(), any(), any(), any()))
				.thenReturn(HoldResult.failed(ReservationFailureReason.INTEGRATION_TIMEOUT));

		ReservationProcess process = useCase.startReservation("juan", LAURA, NEXT_MONDAY, ELEVEN_TWENTY);

		assertThat(process.getStatus()).isEqualTo(ReservationStatus.FAILED);
		assertThat(process.getFailureReason()).isEqualTo(ReservationFailureReason.INTEGRATION_TIMEOUT);
	}

	@Test
	void acceptsTheStartTimeWithSeconds() {
		when(holdPort.createHold(any(), any(), any(), any()))
				.thenReturn(HoldResult.created(new Hold("hold-1", "process-1", NOW)));

		ReservationProcess process = useCase.startReservation("juan", LAURA, NEXT_MONDAY, LocalTime.parse("11:20:00"));

		assertThat(process.getStatus()).isEqualTo(ReservationStatus.AWAITING_REQUEST);
	}

	@Test
	void todayAcceptsASlotThatHasNotStarted() {
		when(holdPort.createHold(any(), any(), any(), any()))
				.thenReturn(HoldResult.created(new Hold("hold-1", "process-1", NOW)));

		ReservationProcess process = useCase.startReservation("juan", LAURA, TODAY, LocalTime.parse("12:40"));

		assertThat(process.getStatus()).isEqualTo(ReservationStatus.AWAITING_REQUEST);
	}

	@Test
	void todayRejectsASlotThatAlreadyStartedWithoutAskingTheCatedra() {
		// A las 12:30 de Argentina, el de 12:20 ya empezó.
		assertRejected(TODAY, LocalTime.parse("12:20"), ReservationException.Code.INVALID_SLOT);
		assertNothingSavedNorHeld();
	}

	@Test
	void rejectsATimeThatIsNotASlotOfTheAgenda() {
		// 11:30 está dentro del horario pero no es el inicio de un slot de 20 minutos.
		assertRejected(NEXT_MONDAY, LocalTime.parse("11:30"), ReservationException.Code.INVALID_SLOT);
		assertNothingSavedNorHeld();
	}

	@Test
	void rejectsADayTheProfessionalDoesNotAttend() {
		assertRejected(LocalDate.parse("2026-11-10"), ELEVEN_TWENTY, ReservationException.Code.INVALID_SLOT);
		assertNothingSavedNorHeld();
	}

	@Test
	void rejectsADateOutOfTheHorizonWithoutAskingAnyone() {
		assertRejected(TODAY.minusDays(1), ELEVEN_TWENTY, ReservationException.Code.DATE_OUT_OF_RANGE);
		assertRejected(TODAY.plusDays(61), ELEVEN_TWENTY, ReservationException.Code.DATE_OUT_OF_RANGE);
		verifyNoInteractions(catalogClientPort, reservationProcessRepository, holdPort);
	}

	@Test
	void rejectsAnUnknownProfessional() {
		when(catalogClientPort.getProfessionalAgenda(LAURA)).thenReturn(Optional.empty());

		assertRejected(NEXT_MONDAY, ELEVEN_TWENTY, ReservationException.Code.PROFESSIONAL_NOT_FOUND);
		assertNothingSavedNorHeld();
	}

	@Test
	void rejectsADisabledProfessional() {
		when(catalogClientPort.getProfessionalAgenda(LAURA)).thenReturn(Optional.of(laura(false)));

		assertRejected(NEXT_MONDAY, ELEVEN_TWENTY, ReservationException.Code.PROFESSIONAL_DISABLED);
		assertNothingSavedNorHeld();
	}

	@Test
	void rejectsAUserWithAnActiveReservationWithoutAskingAnyone() {
		// CU-04, 2a: ni el catálogo ni la cátedra. La garantía ante pedidos simultáneos es de la base y
		// se prueba en el adaptador (ADR-0070).
		when(reservationProcessRepository.existsActiveByUserId(7L)).thenReturn(true);

		assertRejected(NEXT_MONDAY, ELEVEN_TWENTY, ReservationException.Code.ACTIVE_RESERVATION_EXISTS);
		verifyNoInteractions(catalogClientPort);
		assertNothingSavedNorHeld();
	}

	@Test
	void doesNotStartTheReservationWhenTheCatalogIsUnavailable() {
		// CU-04 3a: sin datos vigentes no se guarda nada ni se llama a la cátedra.
		when(catalogClientPort.getProfessionalAgenda(LAURA)).thenThrow(new CatalogUnavailableException("down"));

		assertThatThrownBy(() -> useCase.startReservation("juan", LAURA, NEXT_MONDAY, ELEVEN_TWENTY))
				.isInstanceOf(CatalogUnavailableException.class);
		assertNothingSavedNorHeld();
	}

	private void assertRejected(LocalDate date, LocalTime startTime, ReservationException.Code code) {
		assertThatThrownBy(() -> useCase.startReservation("juan", LAURA, date, startTime))
				.isInstanceOf(ReservationException.class)
				.extracting("code").isEqualTo(code);
	}

	// No se guardó ningún proceso ni se llamó a la cátedra. La consulta de un proceso activo sí puede
	// haber ocurrido: es una lectura.
	private void assertNothingSavedNorHeld() {
		verify(reservationProcessRepository, never()).create(any());
		verify(reservationProcessRepository, never()).update(any(), any());
		verifyNoInteractions(holdPort);
	}

	private void whenTheHoldIsCreated() {
		when(holdPort.createHold(any(), any(), any(), any()))
				.thenReturn(HoldResult.created(new Hold("hold-1", "process-1", NOW)));
	}

	private ReservationProcess saved(ReservationProcess process) {
		savedStatuses.add(process.getStatus());
		return process;
	}

	private static User juan() {
		return User.builder().id(7L).login("juan").firstName("Juan").lastName("Perez")
				.externalPatientId(EXTERNAL_PATIENT_ID).build();
	}

	private static ProfessionalAgenda laura(boolean enabled) {
		return ProfessionalAgenda.builder()
				.id(LAURA)
				.firstName("Laura")
				.lastName("Medina")
				.enabled(enabled)
				.weeklySchedules(List.of(WeeklySchedule.builder()
						.dayOfWeek(DayOfWeek.MONDAY)
						.startTime(LocalTime.parse("11:00"))
						.endTime(LocalTime.parse("15:00"))
						.slotDurationMinutes(20)
						.build()))
				.catalogVersion(1L)
				.build();
	}

}
