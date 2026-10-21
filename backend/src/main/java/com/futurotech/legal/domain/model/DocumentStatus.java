package com.futurotech.legal.domain.model;

/**
 * Estado del documento generado. Salvaguarda jurídica: todo documento nace
 * como BORRADOR y requiere revisión humana de un abogado antes de aprobarse.
 */
public enum DocumentStatus {
    DRAFT,
    REVIEWED,
    APPROVED,
    VOID
}
