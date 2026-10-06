/**
 * Servicio de turnos y reservas.
 *
 * <p>Sigue la arquitectura hexagonal de la cátedra (skill {@code /hexagonal}, ADR-0040). Hay un
 * paquete por feature y, dentro de cada uno, tres capas: {@code domain}, {@code application} e
 * {@code infrastructure}. Lo transversal a las features va en {@code shared}.
 *
 * <p>Regla de dependencias: {@code infrastructure} puede usar {@code application} y {@code domain};
 * {@code application} solo usa {@code domain}; {@code domain} no usa a nadie. La verifica
 * {@code ArchitectureTest}.
 *
 * <p>Para agentes: antes de escribir código, cargar la skill {@code /hexagonal}. Cada paquete tiene
 * un {@code package-info.java} que dice qué va ahí. Una feature nueva repite el árbol de
 * {@code user}; una operación nueva suma un puerto de entrada, su implementación, un método en
 * la fachada y un endpoint.
 */
package com.example.turnos;
