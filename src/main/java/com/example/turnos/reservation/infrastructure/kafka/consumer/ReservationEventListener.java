package com.example.turnos.reservation.infrastructure.kafka.consumer;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import com.example.turnos.reservation.application.service.ReservationService;
import com.example.turnos.reservation.infrastructure.kafka.mapper.ReservationEventMessageMapper;
import com.example.turnos.shared.infrastructure.config.ReservationProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Adaptador de entrada del topic de acciones de la cátedra (REF §15.2). No tiene lógica de negocio:
 * interpreta el mensaje, lo entrega a la fachada y confirma el offset. El topic sale de la integración
 * que se pidió al arrancar (ADR-0005).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReservationEventListener {

	private final ReservationService reservationService;
	private final ReservationEventMessageMapper messageMapper;
	private final ReservationProperties properties;

	@KafkaListener(topics = "#{@catedraIntegration.kafkaAppointmentActionsTopic()}")
	public void onReservationEvent(ConsumerRecord<String, String> record, Acknowledgment acknowledgment) {
		messageMapper.toDomain(record.value()).ifPresent(event -> {
			// Los reintentos son los de ADR-0031 y se cuentan acá: no hay un manejador de errores de Spring
			// que reintente por su cuenta. No hay espera entre uno y otro: el caso típico es un cambio
			// simultáneo del mismo proceso, que se resuelve con releerlo (ADR-0068).
			int attempts = 1 + properties.eventRetries();
			for (int attempt = 1; attempt <= attempts; attempt++) {
				try {
					reservationService.handleReservationEvent(event);
					return;
				} catch (RuntimeException e) {
					log.warn("Reservation event {} failed on attempt {} of {}", event.getEventId(), attempt, attempts,
							e);
				}
			}
			// Agotados los reintentos se sigue: un solo mensaje no puede frenar el topic, y la reconciliación
			// recupera el estado desde la cátedra (ADR-0030, ADR-0031).
			log.error("Reservation event {} could not be processed after {} attempts, skipped", event.getEventId(),
					attempts);
		});
		// Recién ahora, con el evento persistido, ignorado o abandonado (ADR-0031).
		acknowledgment.acknowledge();
	}

}
