package com.futurotech.legal.domain.exception;

/** Metadatos del caso inválidos o campos requeridos faltantes. */
public class InvalidSchemaException extends DomainException {
    public InvalidSchemaException(String message) {
        super(message);
    }
}
