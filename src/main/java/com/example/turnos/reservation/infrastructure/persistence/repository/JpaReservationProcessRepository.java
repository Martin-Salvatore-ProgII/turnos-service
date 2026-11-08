package com.example.turnos.reservation.infrastructure.persistence.repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.example.turnos.reservation.domain.model.ReservationStatus;
import com.example.turnos.reservation.infrastructure.persistence.entity.ReservationProcessEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface JpaReservationProcessRepository extends JpaRepository<ReservationProcessEntity, UUID> {

	Optional<ReservationProcessEntity> findByIdAndUserId(UUID id, Long userId);

	Optional<ReservationProcessEntity> findByReservationProcessId(String reservationProcessId);

	List<ReservationProcessEntity> findByStatusAndCreatedAtBefore(ReservationStatus status, Instant cutoff);

	List<ReservationProcessEntity> findByStatusInAndExpiresAtBefore(Collection<ReservationStatus> statuses, Instant cutoff);

	List<ReservationProcessEntity> findByStatusAndAppointmentDateGreaterThanEqual(ReservationStatus status, LocalDate date);

	boolean existsByUserIdAndStatusIn(Long userId, Collection<ReservationStatus> statuses);

}
