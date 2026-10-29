package com.example.turnos.availability.infrastructure.rest.mapper;

import com.example.turnos.availability.domain.model.Occupancy;
import com.example.turnos.availability.infrastructure.rest.dto.OccupancyDto;
import org.springframework.stereotype.Component;

@Component
public class OccupancyMapper {

	public Occupancy toDomainModel(OccupancyDto dto) {
		if (dto == null) {
			return null;
		}
		return Occupancy.builder()
				.date(dto.getDate())
				.startTime(dto.getStartTime())
				.status(dto.getStatus())
				.expiresAt(dto.getExpiresAt())
				.build();
	}

}
