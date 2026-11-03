package com.example.turnos.shared.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

// Tamaño de página por defecto y máximo de las listas paginadas (docs/arq/contratos.md, RNF-09).
@ConfigurationProperties("turnos.pagination")
public record PaginationProperties(int defaultSize, int maxSize) {
}
