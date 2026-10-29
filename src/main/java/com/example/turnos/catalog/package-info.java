/**
 * Feature del catálogo visto desde turnos: la agenda vigente de un profesional, pedida al servicio
 * de catálogo en cada operación que la necesita (ADR-0017).
 *
 * <p>No es una copia del catálogo: turnos no guarda estos datos para usarlos después (Constitución
 * P-05). No tiene casos de uso porque no tiene reglas propias: la disponibilidad y la reserva usan
 * su puerto de salida (ADR-0062).
 */
package com.example.turnos.catalog;
