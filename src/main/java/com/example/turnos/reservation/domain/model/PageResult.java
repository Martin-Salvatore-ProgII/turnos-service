package com.example.turnos.reservation.domain.model;

import java.util.List;
import java.util.function.Function;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Una página de resultados y el total de elementos que cumplen los filtros, en todas las páginas.
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PageResult<T> {
	private List<T> content;
	private long totalElements;
	private int page;
	private int size;

	// La misma página, con cada elemento convertido.
	public <R> PageResult<R> map(Function<T, R> converter) {
		return PageResult.<R>builder()
				.content(content.stream().map(converter).toList())
				.totalElements(totalElements)
				.page(page)
				.size(size)
				.build();
	}
}
