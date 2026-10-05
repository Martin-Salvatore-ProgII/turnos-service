/**
 * Fachada de la feature: {@code <Feature>Service}, anotada {@code @Service}.
 *
 * <p>Inyecta todos los puertos de entrada de la feature y es lo único que usan los adaptadores de
 * entrada (controllers, listeners, schedulers). Traduce un {@code Optional} vacío en la excepción
 * de aplicación.
 *
 * <p>Para agentes: no eliminarla por redundante; es parte del patrón de la cátedra.
 */
package com.example.turnos.user.application.service;
