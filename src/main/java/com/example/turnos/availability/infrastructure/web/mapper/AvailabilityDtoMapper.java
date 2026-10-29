package com.example.turnos.availability.infrastructure.web.mapper;

import java.util.List;

import com.example.turnos.availability.domain.model.Availability;
import com.example.turnos.availability.domain.model.Slot;
import com.example.turnos.availability.infrastructure.web.dto.AvailabilityResponse;
import com.example.turnos.availability.infrastructure.web.dto.SlotResponse;
import org.springframework.stereotype.Component;

@Component
public class AvailabilityDtoMapper {

	public AvailabilityResponse toResponse(Availability availability) {
		if (availability == null) {
			return null;
		}
		List<SlotResponse> slots = availability.getSlots() == null
				? List.of()
				: availability.getSlots().stream().map(this::toResponse).toList();
		return AvailabilityResponse.builder()
				.professionalId(availability.getProfessionalId())
				.professionalFirstName(availability.getProfessionalFirstName())
				.professionalLastName(availability.getProfessionalLastName())
				.date(availability.getDate())
				.catalogVersion(availability.getCatalogVersion())
				.slots(slots)
				.build();
	}

	private SlotResponse toResponse(Slot slot) {
		if (slot == null) {
			return null;
		}
		return SlotResponse.builder()
				.startTime(slot.getStartTime())
				.endTime(slot.getEndTime())
				.build();
	}

}
