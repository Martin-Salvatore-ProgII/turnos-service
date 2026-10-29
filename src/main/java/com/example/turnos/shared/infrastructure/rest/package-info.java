/**
 * Piezas comunes de los clientes REST hacia la cátedra y el catálogo: el reintento de una lectura
 * (ADR-0029) y cómo se informa una falla sin mostrar la dirección del servicio (ADR-0063).
 *
 * <p>Los clientes HTTP en sí ({@code catedraRestClient}, {@code catalogRestClient}) se arman en
 * {@code config}.
 */
package com.example.turnos.shared.infrastructure.rest;
