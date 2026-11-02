/**
 * Adaptadores REST hacia la cátedra: el hold y la confirmación (REF §9, §10), con el
 * {@code catedraRestClient}. Son operaciones con efectos: no usan el reintento de las lecturas, cada
 * una sigue su propia regla (ADR-0028, ADR-0029).
 */
package com.example.turnos.reservation.infrastructure.rest.adapter;
