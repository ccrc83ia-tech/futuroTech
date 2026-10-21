package com.futurotech.legal.domain.exception;

/** No se encontró la firma (tenant) solicitada. */
public class TenantNotFoundException extends DomainException {
    public TenantNotFoundException(String tenantId) {
        super("Firma no encontrada: " + tenantId);
    }
}
