/**
 * Manejador global de excepciones ({@code @RestControllerAdvice}).
 *
 * <p>Traduce las excepciones de aplicación y los errores de validación a
 * {@code application/problem+json} con {@code status} y {@code code}, según el contrato.
 */
package com.example.turnos.shared.infrastructure.web.advice;
