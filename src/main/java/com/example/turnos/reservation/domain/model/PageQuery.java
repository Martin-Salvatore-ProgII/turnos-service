package com.example.turnos.reservation.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Qué página se pide. Es un tipo propio y no el Pageable de Spring Data, porque el dominio no
// depende de frameworks (skill hexagonal, ArchitectureTest).
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PageQuery {
	// Desde 0.
	private int page;
	private int size;
}
