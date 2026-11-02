package com.example.turnos.reservation.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.example.turnos.reservation.application.exception.ReservationException;
import com.example.turnos.reservation.application.exception.ReservationProcessConflictException;
import com.example.turnos.reservation.domain.model.PhoneSubmission;
import com.example.turnos.reservation.domain.model.ReservationProcess;
import com.example.turnos.reservation.domain.model.ReservationStatus;
import com.example.turnos.reservation.domain.ports.out.AdditionalInformationPublisherPort;
import com.example.turnos.reservation.domain.ports.out.ReservationProcessRepository;
import com.example.turnos.user.domain.model.User;
import com.example.turnos.user.domain.ports.out.UserRepository;

class SubmitPhoneUseCaseImplTest {

	private static final Instant NOW = Instant.parse("2026-11-02T15:30:00Z");
	private static final UUID ID = UUID.randomUUID();

	private final UserRepository userRepository = mock(UserRepository.class);
	private final ReservationProcessRepository reservationProcessRepository = mock(ReservationProcessRepository.class);
	private final AdditionalInformationPublisherPort publisherPort = mock(AdditionalInformationPublisherPort.class);

	private SubmitPhoneUseCaseImpl useCase;

	@BeforeEach
	void setUp() {
		useCase = new SubmitPhoneUseCaseImpl(userRepository, reservationProcessRepository, publisherPort,
				Clock.fixed(NOW, ZoneOffset.UTC));
		when(userRepository.findByLogin("juan")).thenReturn(Optional.of(User.builder().id(7L).login("juan").build()));
		when(reservationProcessRepository.update(any(), any()))
				.thenAnswer(invocation -> Optional.of(invocation.getArgument(1)));
	}

	@Test
	void publishesTheNormalizedPhoneWithTheStoredRequestEventIdAndThenMovesToPhoneSubmitted() {
		ReservationProcess process = awaitingPhone();
		when(reservationProcessRepository.findByIdAndUserId(ID, 7L)).thenReturn(Optional.of(process));
		when(publisherPort.publishPhone(any())).thenReturn(true);

		ReservationProcess result = useCase.submitPhone("juan", ID, "+54 9 261 555-1234");

		ArgumentCaptor<PhoneSubmission> sent = ArgumentCaptor.forClass(PhoneSubmission.class);
		verify(publisherPort).publishPhone(sent.capture());
		assertThat(sent.getValue().getPhoneNumber()).isEqualTo("+5492615551234");
		assertThat(sent.getValue().getRequestEventId()).isEqualTo("request-event-8a31");
		assertThat(sent.getValue().getReservationProcessId()).isEqualTo("process-a92b47d0");
		assertThat(sent.getValue().getHoldId()).isEqualTo("hold-6c0c2f41");
		assertThat(sent.getValue().getEventId()).isNotBlank();
		assertThat(sent.getValue().getOccurredAt()).isEqualTo(NOW);
		assertThat(result.getStatus()).isEqualTo(ReservationStatus.PHONE_SUBMITTED);
		assertThat(result.getSubmittedEventId()).isEqualTo(sent.getValue().getEventId());
	}

	@Test
	void everySubmissionOfTheUserHasANewEventId() {
		when(reservationProcessRepository.findByIdAndUserId(ID, 7L)).thenAnswer(i -> Optional.of(awaitingPhone()));
		when(publisherPort.publishPhone(any())).thenReturn(true);

		useCase.submitPhone("juan", ID, "2615551234");
		useCase.submitPhone("juan", ID, "2615551234");

		ArgumentCaptor<PhoneSubmission> sent = ArgumentCaptor.forClass(PhoneSubmission.class);
		verify(publisherPort, org.mockito.Mockito.times(2)).publishPhone(sent.capture());
		assertThat(sent.getAllValues().get(0).getEventId()).isNotEqualTo(sent.getAllValues().get(1).getEventId());
	}

	@Test
	void rejectsAReservationThatIsNotTheirsOrDoesNotExistWithoutPublishing() {
		when(reservationProcessRepository.findByIdAndUserId(ID, 7L)).thenReturn(Optional.empty());

		assertRejected(ReservationException.Code.RESERVATION_NOT_FOUND);
		verifyNoInteractions(publisherPort);
	}

	@Test
	void rejectsAProcessThatIsNotWaitingForThePhoneAndSaysWhichStateItIsIn() {
		ReservationProcess expired = awaitingPhone();
		expired.setStatus(ReservationStatus.EXPIRED);
		when(reservationProcessRepository.findByIdAndUserId(ID, 7L)).thenReturn(Optional.of(expired));

		assertThatThrownBy(() -> useCase.submitPhone("juan", ID, "2615551234"))
				.isInstanceOfSatisfying(ReservationException.class, e -> {
					assertThat(e.getCode()).isEqualTo(ReservationException.Code.PHONE_NOT_REQUESTED);
					assertThat(e.getMessage()).contains("EXPIRED");
				});
		verifyNoInteractions(publisherPort);
	}

	@Test
	void rejectsAnInvalidPhoneWithoutPublishingAndKeepsWaiting() {
		ReservationProcess process = awaitingPhone();
		when(reservationProcessRepository.findByIdAndUserId(ID, 7L)).thenReturn(Optional.of(process));

		assertRejected(ReservationException.Code.INVALID_PHONE_NUMBER, "12345");

		verifyNoInteractions(publisherPort);
		verify(reservationProcessRepository, never()).update(any(), any());
		assertThat(process.getStatus()).isEqualTo(ReservationStatus.AWAITING_PHONE);
	}

	@Test
	void whenTheBrokerDoesNotConfirmTheProcessKeepsWaitingAndTheUserCanTryAgain() {
		ReservationProcess process = awaitingPhone();
		when(reservationProcessRepository.findByIdAndUserId(ID, 7L)).thenReturn(Optional.of(process));
		when(publisherPort.publishPhone(any())).thenReturn(false);

		assertRejected(ReservationException.Code.INTEGRATION_UNAVAILABLE);

		verify(reservationProcessRepository, never()).update(any(), any());
		assertThat(process.getStatus()).isEqualTo(ReservationStatus.AWAITING_PHONE);
		assertThat(process.getSubmittedEventId()).isNull();
	}

	// El teléfono ya está publicado y la cátedra confirmó antes de que se guardara el envío: manda el evento.
	@Test
	void anEventThatArrivedWhilePublishingIsNotOverwritten() {
		ReservationProcess stale = awaitingPhone();
		ReservationProcess confirmed = awaitingPhone();
		confirmed.setStatus(ReservationStatus.CONFIRMED);
		when(reservationProcessRepository.findByIdAndUserId(ID, 7L)).thenReturn(Optional.of(stale));
		when(publisherPort.publishPhone(any())).thenReturn(true);
		doAnswer(invocation -> {
			throw new ReservationProcessConflictException(ID);
		}).when(reservationProcessRepository).update(any(), any());
		when(reservationProcessRepository.findById(ID)).thenReturn(Optional.of(confirmed));

		ReservationProcess result = useCase.submitPhone("juan", ID, "2615551234");

		assertThat(result.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
		assertThat(result.getSubmittedEventId()).isNull();
	}

	private void assertRejected(ReservationException.Code code) {
		assertRejected(code, "2615551234");
	}

	private void assertRejected(ReservationException.Code code, String phone) {
		assertThatThrownBy(() -> useCase.submitPhone("juan", ID, phone))
				.isInstanceOfSatisfying(ReservationException.class, e -> assertThat(e.getCode()).isEqualTo(code));
	}

	private static ReservationProcess awaitingPhone() {
		return ReservationProcess.builder().id(ID).userId(7L).status(ReservationStatus.AWAITING_PHONE)
				.holdId("hold-6c0c2f41").reservationProcessId("process-a92b47d0").requestEventId("request-event-8a31")
				.build();
	}

}
