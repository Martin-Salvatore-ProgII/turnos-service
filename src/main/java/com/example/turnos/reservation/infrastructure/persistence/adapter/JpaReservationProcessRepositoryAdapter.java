package com.example.turnos.reservation.infrastructure.persistence.adapter;

import java.util.Optional;
import java.util.UUID;

import com.example.turnos.reservation.application.exception.ReservationProcessConflictException;
import com.example.turnos.reservation.domain.model.ReservationProcess;
import com.example.turnos.reservation.domain.ports.out.ReservationProcessRepository;
import com.example.turnos.reservation.infrastructure.persistence.entity.ReservationProcessEntity;
import com.example.turnos.reservation.infrastructure.persistence.mapper.ReservationProcessMapper;
import com.example.turnos.reservation.infrastructure.persistence.repository.JpaReservationProcessRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JpaReservationProcessRepositoryAdapter implements ReservationProcessRepository {

	private final JpaReservationProcessRepository jpaReservationProcessRepository;
	private final ReservationProcessMapper reservationProcessMapper;

	// saveAndFlush y no save: el proceso tiene que quedar en la base antes de llamar a la cátedra
	// (ADR-0022), y así un error de la base aparece acá y no más tarde.
	@Override
	public ReservationProcess create(ReservationProcess process) {
		ReservationProcessEntity savedEntity = jpaReservationProcessRepository.saveAndFlush(
				reservationProcessMapper.toEntity(process));
		return reservationProcessMapper.toDomainModel(savedEntity);
	}

	// El conflicto de versión se traduce a la excepción de la feature: el caso de uso no conoce las
	// excepciones de Spring ni de JPA (ADR-0068).
	@Override
	public Optional<ReservationProcess> update(UUID id, ReservationProcess process) {
		if (!jpaReservationProcessRepository.existsById(id)) {
			return Optional.empty();
		}
		ReservationProcessEntity entity = reservationProcessMapper.toEntity(process);
		entity.setId(id);
		try {
			ReservationProcessEntity updatedEntity = jpaReservationProcessRepository.saveAndFlush(entity);
			return Optional.of(reservationProcessMapper.toDomainModel(updatedEntity));
		} catch (OptimisticLockingFailureException e) {
			throw new ReservationProcessConflictException(id);
		}
	}

}
