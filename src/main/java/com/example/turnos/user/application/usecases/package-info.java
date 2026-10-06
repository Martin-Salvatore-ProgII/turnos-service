/**
 * Implementaciones de los puertos de entrada: acá vive la lógica de negocio.
 *
 * <p>Una clase {@code <Verbo><Feature>UseCaseImpl} por puerto, anotada {@code @Component} (no
 * {@code @Service}) con {@code @RequiredArgsConstructor} y campos {@code private final}. Depende
 * solo de puertos de salida; nunca importa nada de {@code infrastructure}.
 *
 * <p>Se prueba con JUnit y Mockito, sin contexto de Spring.
 */
package com.example.turnos.user.application.usecases;
