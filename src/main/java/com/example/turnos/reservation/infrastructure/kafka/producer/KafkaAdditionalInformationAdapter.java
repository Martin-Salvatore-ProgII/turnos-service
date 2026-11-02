package com.example.turnos.reservation.infrastructure.kafka.producer;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.kafka.common.KafkaException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import com.example.turnos.reservation.domain.model.PhoneSubmission;
import com.example.turnos.reservation.domain.ports.out.AdditionalInformationPublisherPort;
import com.example.turnos.reservation.infrastructure.kafka.mapper.AdditionalInformationMessageMapper;
import com.example.turnos.shared.infrastructure.config.CatedraIntegration;
import com.example.turnos.shared.infrastructure.config.CatedraProperties;
import com.example.turnos.shared.infrastructure.rest.RemoteCommandRetry;
import com.example.turnos.shared.infrastructure.rest.RemoteServiceUnavailableException;
import com.example.turnos.shared.infrastructure.rest.TransientRemoteFailureException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaAdditionalInformationAdapter implements AdditionalInformationPublisherPort {

	private final KafkaTemplate<String, String> catedraKafkaTemplate;
	private final CatedraIntegration integration;
	private final CatedraProperties catedraProperties;
	private final RemoteCommandRetry remoteCommandRetry;
	private final AdditionalInformationMessageMapper messageMapper;
	private final JsonMapper jsonMapper;

	// Se serializa una sola vez: todos los intentos publican exactamente el mismo mensaje, con el mismo
	// eventId, y la cátedra reconoce el duplicado (ADR-0029). La key es el reservationProcessId, como exige
	// la cátedra y para conservar el orden dentro del proceso (REF §15.1, §15.5).
	@Override
	public boolean publishPhone(PhoneSubmission submission) {
		String body = jsonMapper.writeValueAsString(messageMapper.toMessage(submission, integration.groupId()));
		String operation = "Publish phone of process " + submission.getReservationProcessId();
		try {
			remoteCommandRetry.execute(operation, () -> send(submission.getReservationProcessId(), body));
			return true;
		} catch (RemoteServiceUnavailableException e) {
			// El mensaje no tiene direcciones: dice la operación, los intentos y el tipo de falla.
			log.error("{}", e.getMessage());
			return false;
		}
	}

	// Espera la confirmación del broker, un poco más que las esperas internas del productor (que valen el
	// timeout de respuesta): el límite real lo pone el productor y este solo evita colgarse.
	private Boolean send(String key, String body) {
		try {
			catedraKafkaTemplate.send(integration.kafkaAppointmentPhoneTopic(), key, body)
					.get(catedraProperties.readTimeout().toMillis() + 1000, TimeUnit.MILLISECONDS);
			return Boolean.TRUE;
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new TransientRemoteFailureException("Interrupted while publishing", e);
		} catch (ExecutionException | TimeoutException | KafkaException
				| org.springframework.kafka.KafkaException e) {
			// Las dos KafkaException: la del cliente llega dentro del futuro; la de Spring sale de send() cuando
			// no se pudo ni armar el envío (broker inalcanzable, producer que no se construye).
			throw new TransientRemoteFailureException("The broker did not confirm the message", e);
		}
	}

}
