/**
 * Puertos de salida: lo que la feature necesita del exterior (las ocupaciones de la cátedra),
 * expresado en tipos de dominio.
 *
 * <p>Sin tipos de Jackson ni HTTP en las firmas. Los implementa un adaptador en
 * {@code infrastructure}. La agenda vigente no se declara acá: se usa el puerto de la feature
 * {@code catalog} (ADR-0062).
 */
package com.example.turnos.availability.domain.ports.out;
