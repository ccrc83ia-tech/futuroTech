package com.futurotech.legal.domain.exception;

/** El recurso solicitado no existe (documento, entidad, plantilla, etc.). */
public class ResourceNotFoundException extends DomainException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
