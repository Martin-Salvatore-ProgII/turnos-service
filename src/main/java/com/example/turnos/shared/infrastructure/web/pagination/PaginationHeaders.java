package com.example.turnos.shared.infrastructure.web.pagination;

import java.util.ArrayList;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * Las cabeceras de una página: el total en {@code X-Total-Count} y los enlaces a las páginas
 * vecinas en {@code Link}, como hace la cátedra (REF §11). Los enlaces repiten el pedido actual,
 * con sus filtros, cambiando solo {@code page}.
 */
public final class PaginationHeaders {

	public static final String TOTAL_COUNT = "X-Total-Count";

	private PaginationHeaders() {
	}

	public static HttpHeaders of(long totalElements, int page, int size) {
		HttpHeaders headers = new HttpHeaders();
		headers.add(TOTAL_COUNT, Long.toString(totalElements));

		int lastPage = (int) Math.max((totalElements + size - 1) / size - 1, 0);
		List<String> links = new ArrayList<>();
		if (page < lastPage) {
			links.add(link(page + 1, "next"));
		}
		if (page > 0) {
			links.add(link(Math.min(page - 1, lastPage), "prev"));
		}
		links.add(link(lastPage, "last"));
		links.add(link(0, "first"));
		headers.add(HttpHeaders.LINK, String.join(",", links));
		return headers;
	}

	// El query string del pedido ya viene codificado ("ana%20gom"): build(true) lo toma tal cual. Sin
	// eso se codificaría otra vez y el enlace diría "ana%2520gom", otra búsqueda.
	private static String link(int page, String relation) {
		String uri = ServletUriComponentsBuilder.fromCurrentRequest()
				.replaceQueryParam("page", page)
				.build(true)
				.toUriString();
		return "<" + uri + ">; rel=\"" + relation + "\"";
	}

}
