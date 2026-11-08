package com.example.turnos.reservation.infrastructure.persistence.adapter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.example.turnos.reservation.application.exception.ReservationException;
import com.example.turnos.reservation.application.exception.ReservationProcessConflictException;
import com.example.turnos.reservation.domain.model.PageQuery;
import com.example.turnos.reservation.domain.model.PageResult;
import com.example.turnos.reservation.domain.model.ReservationProcess;
import com.example.turnos.reservation.domain.model.ReservationStatus;
import com.example.turnos.reservation.domain.ports.out.ReservationProcessRepository;
import com.example.turnos.reservation.infrastructure.persistence.entity.ReservationProcessEntity;
import com.example.turnos.reservation.infrastructure.persistence.mapper.ReservationProcessMapper;
import com.example.turnos.reservation.infrastructure.persistence.repository.JpaReservationProcessRepository;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JpaReservationProcessRepositoryAdapter implements ReservationProcessRepository {

	// El índice de la migración V3: un solo proceso activo por usuario (ADR-0024, ADR-0070).
	static final String ACTIVE_USER_INDEX = "uq_reservation_process_active_user";

	private final JpaReservationProcessRepository jpaReservationProcessRepository;
	private final ReservationProcessMapper reservationProcessMapper;

	// saveAndFlush y no save: el proceso tiene que quedar en la base antes de llamar a la cátedra
	// (ADR-0022), y así un error de la base aparece acá y no más tarde.
	// El rechazo del índice de un proceso activo se traduce al error funcional, igual que si lo hubiera
	// detectado la consulta previa (ADR-0070). Cualquier otra violación sigue de largo: es un error interno.
	@Override
	public ReservationProcess create(ReservationProcess process) {
		try {
			ReservationProcessEntity savedEntity = jpaReservationProcessRepository.saveAndFlush(
					reservationProcessMapper.toEntity(process));
			return reservationProcessMapper.toDomainModel(savedEntity);
		} catch (DataIntegrityViolationException e) {
			if (violates(e, ACTIVE_USER_INDEX)) {
				throw new ReservationException(ReservationException.Code.ACTIVE_RESERVATION_EXISTS,
						"User " + process.getUserId() + " already has an active reservation");
			}
			throw e;
		}
	}

	@Override
	public Optional<ReservationProcess> findByIdAndUserId(UUID id, Long userId) {
		return jpaReservationProcessRepository.findByIdAndUserId(id, userId)
				.map(reservationProcessMapper::toDomainModel);
	}

	// Del más reciente al más antiguo (contratos.md). El id desempata dos procesos creados en el mismo
	// instante: sin un orden total, un proceso podría repetirse en dos páginas o no salir en ninguna.
	@Override
	public PageResult<ReservationProcess> findByUserId(Long userId, ReservationStatus status, PageQuery page) {
		PageRequest pageRequest = PageRequest.of(page.getPage(), page.getSize(),
				Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
		Page<ReservationProcessEntity> result = status == null
				? jpaReservationProcessRepository.findByUserId(userId, pageRequest)
				: jpaReservationProcessRepository.findByUserIdAndStatus(userId, status, pageRequest);
		return PageResult.<ReservationProcess>builder()
				.content(result.getContent().stream().map(reservationProcessMapper::toDomainModel).toList())
				.totalElements(result.getTotalElements())
				.page(page.getPage())
				.size(page.getSize())
				.build();
	}

	@Override
	public Optional<ReservationProcess> findById(UUID id) {
		return jpaReservationProcessRepository.findById(id).map(reservationProcessMapper::toDomainModel);
	}

	@Override
	public Optional<ReservationProcess> findByReservationProcessId(String reservationProcessId) {
		return jpaReservationProcessRepository.findByReservationProcessId(reservationProcessId)
				.map(reservationProcessMapper::toDomainModel);
	}

	@Override
	public List<ReservationProcess> findStartedCreatedBefore(Instant cutoff) {
		return jpaReservationProcessRepository.findByStatusAndCreatedAtBefore(ReservationStatus.STARTED, cutoff)
				.stream().map(reservationProcessMapper::toDomainModel).toList();
	}

	@Override
	public List<ReservationProcess> findByStatusInAndExpiresAtBefore(Collection<ReservationStatus> statuses,
			Instant cutoff) {
		return jpaReservationProcessRepository.findByStatusInAndExpiresAtBefore(statuses, cutoff).stream()
				.map(reservationProcessMapper::toDomainModel).toList();
	}

	@Override
	public List<ReservationProcess> findConfirmedFrom(LocalDate date) {
		return jpaReservationProcessRepository
				.findByStatusAndAppointmentDateGreaterThanEqual(ReservationStatus.CONFIRMED, date).stream()
				.map(reservationProcessMapper::toDomainModel).toList();
	}

	@Override
	public boolean existsActiveByUserId(Long userId) {
		return jpaReservationProcessRepository.existsByUserIdAndStatusIn(userId, ReservationStatus.active());
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

	// Se decide por el nombre de la restricción que informa la base, no por el texto del error.
	private static boolean violates(DataIntegrityViolationException e, String constraint) {
		for (Throwable cause = e; cause != null; cause = cause.getCause()) {
			if (cause instanceof ConstraintViolationException violation) {
				return constraint.equals(violation.getConstraintName());
			}
		}
		return false;
	}

}
