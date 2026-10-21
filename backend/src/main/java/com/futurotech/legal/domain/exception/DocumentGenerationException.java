package com.futurotech.legal.domain.exception;

/** Falla durante la generación del documento (IA, exportación, etc.). */
public class DocumentGenerationException extends DomainException {
    public DocumentGenerationException(String message) {
        super(message);
    }

    public DocumentGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}
