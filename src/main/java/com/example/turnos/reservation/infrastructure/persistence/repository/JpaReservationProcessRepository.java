package com.example.turnos.reservation.infrastructure.persistence.repository;

import java.util.Collection;
import java.util.UUID;

import com.example.turnos.reservation.domain.model.ReservationStatus;
import com.example.turnos.reservation.infrastructure.persistence.entity.ReservationProcessEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface JpaReservationProcessRepository extends JpaRepository<ReservationProcessEntity, UUID> {

	boolean existsByUserIdAndStatusIn(Long userId, Collection<ReservationStatus> statuses);

}
