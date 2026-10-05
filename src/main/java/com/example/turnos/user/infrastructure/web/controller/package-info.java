/**
 * Controller REST de la feature: {@code <Feature>Controller}.
 *
 * <p>Inyecta solo la fachada {@code <Feature>Service} y el {@code <Feature>DtoMapper}. Convierte
 * DTO a dominio, llama a la fachada, convierte la respuesta y elige el código HTTP. Sin lógica de
 * negocio y sin {@code try/catch}: los errores los resuelve el manejador de
 * {@code shared.infrastructure.web.advice}.
 *
 * <p>El contrato está en {@code docs/arq/contratos.md}.
 */
package com.example.turnos.user.infrastructure.web.controller;
