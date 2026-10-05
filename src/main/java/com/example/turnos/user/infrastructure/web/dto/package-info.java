/**
 * DTO de la API: {@code <Feature>Request} y {@code <Feature>Response}.
 *
 * <p>Las validaciones de formato ({@code jakarta.validation}) van en el request, no en el modelo
 * de dominio. Los nombres de campo siguen el contrato, en camelCase. Un DTO nunca llega a
 * {@code application} ni a {@code domain}.
 */
package com.example.turnos.user.infrastructure.web.dto;
