/**
 * Puertos de entrada: una interfaz por caso de uso, con un único método.
 *
 * <p>Nombre {@code <Verbo><Feature>UseCase}; el método repite el nombre en camelCase. Firmas en
 * tipos de dominio. Una consulta que puede no encontrar nada devuelve {@code Optional}: el puerto no
 * lanza excepciones.
 *
 * <p>Para agentes: una operación nueva es una interfaz nueva acá, nunca un método más en una
 * interfaz existente.
 */
package com.example.turnos.user.domain.ports.in;
