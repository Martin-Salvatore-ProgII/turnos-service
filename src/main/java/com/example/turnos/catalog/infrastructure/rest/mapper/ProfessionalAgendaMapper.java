package com.example.turnos.catalog.infrastructure.rest.mapper;

import java.util.List;

import com.example.turnos.catalog.domain.model.Category;
import com.example.turnos.catalog.domain.model.ProfessionalAgenda;
import com.example.turnos.catalog.domain.model.WeeklySchedule;
import com.example.turnos.catalog.infrastructure.rest.dto.CategoryDto;
import com.example.turnos.catalog.infrastructure.rest.dto.ProfessionalAgendaResponse;
import com.example.turnos.catalog.infrastructure.rest.dto.WeeklyScheduleDto;
import org.springframework.stereotype.Component;

@Component
public class ProfessionalAgendaMapper {

	public ProfessionalAgenda toDomainModel(ProfessionalAgendaResponse response) {
		if (response == null) {
			return null;
		}
		List<WeeklySchedule> schedules = response.getWeeklySchedules() == null
				? List.of()
				: response.getWeeklySchedules().stream().map(this::toDomainModel).toList();
		return ProfessionalAgenda.builder()
				.id(response.getId())
				.firstName(response.getFirstName())
				.lastName(response.getLastName())
				.category(toDomainModel(response.getCategory()))
				// Sin el dato, el profesional no se da por habilitado.
				.enabled(Boolean.TRUE.equals(response.getEnabled()))
				.weeklySchedules(schedules)
				.catalogVersion(response.getCatalogVersion())
				.build();
	}

	private Category toDomainModel(CategoryDto dto) {
		if (dto == null) {
			return null;
		}
		return Category.builder()
				.id(dto.getId())
				.name(dto.getName())
				.build();
	}

	private WeeklySchedule toDomainModel(WeeklyScheduleDto dto) {
		if (dto == null) {
			return null;
		}
		return WeeklySchedule.builder()
				.id(dto.getId())
				.dayOfWeek(dto.getDayOfWeek())
				.startTime(dto.getStartTime())
				.endTime(dto.getEndTime())
				.slotDurationMinutes(dto.getSlotDurationMinutes() == null ? 0 : dto.getSlotDurationMinutes())
				.build();
	}

}
