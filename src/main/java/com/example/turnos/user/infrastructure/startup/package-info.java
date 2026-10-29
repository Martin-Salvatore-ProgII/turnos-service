/**
 * Adaptadores de entrada que se disparan al arrancar el servicio.
 *
 * <p>Cumplen el mismo papel que un controller, pero quien los llama es el arranque de la
 * aplicación y no un pedido HTTP: leen la configuración, llaman a la fachada
 * {@code <Feature>Service} y no tienen lógica de negocio.
 */
package com.example.turnos.user.infrastructure.startup;
