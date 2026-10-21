package com.futurotech.legal.domain.model;

/**
 * Entidad destinataria del documento (EPS, banco, juzgado, etc.).
 * Se llama RecipientEntity para no colisionar con jakarta.persistence @Entity.
 */
public record RecipientEntity(
        String entityId,
        String name,
        String nit,
        String address,
        String city,
        String phone,
        String email,
        String legalRep,
        String entityType,
        String notes
) {
}
