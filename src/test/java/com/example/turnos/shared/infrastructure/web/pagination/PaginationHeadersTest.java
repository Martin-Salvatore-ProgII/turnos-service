package com.example.turnos.shared.infrastructure.web.pagination;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

// Sin Spring: se simula el pedido en curso, del que salen los enlaces.
class PaginationHeadersTest {

	private static final String BASE = "http://localhost/api/reservations";

	@BeforeEach
	void simulateTheCurrentRequest() {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/reservations");
		request.setQueryString("status=CONFIRMED&page=1&size=10");
		RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
	}

	@AfterEach
	void clearTheRequest() {
		RequestContextHolder.resetRequestAttributes();
	}

	@Test
	void informsTheTotal() {
		assertThat(PaginationHeaders.of(25, 1, 10).getFirst(PaginationHeaders.TOTAL_COUNT)).isEqualTo("25");
	}

	@Test
	void aMiddlePageLinksToAllItsNeighbours() {
		assertThat(links(PaginationHeaders.of(25, 1, 10))).isEqualTo(
				link(2, "next") + "," + link(0, "prev") + "," + link(2, "last") + "," + link(0, "first"));
	}

	@Test
	void theFirstPageHasNoPrevious() {
		assertThat(links(PaginationHeaders.of(25, 0, 10))).isEqualTo(
				link(1, "next") + "," + link(2, "last") + "," + link(0, "first"));
	}

	@Test
	void theLastPageHasNoNext() {
		assertThat(links(PaginationHeaders.of(25, 2, 10))).isEqualTo(
				link(1, "prev") + "," + link(2, "last") + "," + link(0, "first"));
	}

	@Test
	void anExactMultipleDoesNotAddAnEmptyLastPage() {
		assertThat(links(PaginationHeaders.of(20, 0, 10))).contains(link(1, "last"));
	}

	@Test
	void withoutResultsTheOnlyPageIsTheFirst() {
		HttpHeaders headers = PaginationHeaders.of(0, 0, 10);

		assertThat(headers.getFirst(PaginationHeaders.TOTAL_COUNT)).isEqualTo("0");
		assertThat(links(headers)).isEqualTo(link(0, "last") + "," + link(0, "first"));
	}

	@Test
	void aPageAfterTheLastPointsBackToTheLast() {
		assertThat(links(PaginationHeaders.of(25, 7, 10))).isEqualTo(
				link(2, "prev") + "," + link(2, "last") + "," + link(0, "first"));
	}

	@Test
	void keepsTheEncodedParametersAsTheyCame() {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/reservations");
		request.setQueryString("status=CONFIRMED&note=r%C3%ADo%20azul&page=0");
		RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

		// Un parámetro con espacios o tildes no se codifica dos veces ("%2520"), o el enlace pediría otra cosa.
		assertThat(links(PaginationHeaders.of(5, 0, 10))).isEqualTo(
				"<" + BASE + "?status=CONFIRMED&note=r%C3%ADo%20azul&page=0>; rel=\"last\",<" + BASE
						+ "?status=CONFIRMED&note=r%C3%ADo%20azul&page=0>; rel=\"first\"");
	}

	private String links(HttpHeaders headers) {
		return headers.getFirst(HttpHeaders.LINK);
	}

	// Conserva los demás parámetros del pedido y cambia solo la página, que queda al final.
	private String link(int page, String relation) {
		return "<" + BASE + "?status=CONFIRMED&size=10&page=" + page + ">; rel=\"" + relation + "\"";
	}

}
