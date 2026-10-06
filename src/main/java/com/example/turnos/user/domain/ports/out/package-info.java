/**
 * Puertos de salida: lo que la feature necesita del exterior (base de datos, REST y Kafka de la
 * cátedra, servicio de catálogo), expresado en tipos de dominio.
 *
 * <p>Nombre {@code <Feature>Repository} para persistencia y {@code <Cosa>Port} para el resto. Sin
 * tipos de JPA, Jackson, Kafka ni HTTP en las firmas. Los implementa un adaptador en
 * {@code infrastructure}.
 */
package com.example.turnos.user.domain.ports.out;
