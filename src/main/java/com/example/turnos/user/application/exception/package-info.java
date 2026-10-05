/**
 * Excepciones de aplicación de la feature.
 *
 * <p>Extienden {@code RuntimeException} y no conocen HTTP ni Spring. Su traducción a
 * {@code problem+json} la hace el manejador de {@code shared.infrastructure.web.advice}.
 */
package com.example.turnos.user.application.exception;
