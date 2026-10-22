package com.futurotech.legal.domain.model;

/**
 * Documento del caso aportado por el usuario al generar (p. ej. el derecho de
 * petición negado que respalda una tutela). Es efímero: su texto se inyecta en
 * el prompt de esa generación como "hechos del caso", no se indexa ni persiste
 * como fuente normativa. Solo el nombre queda en la auditoría para trazabilidad.
 */
public record CaseAttachment(
        String fileName,
        String extractedText
) {
    public CaseAttachment {
        fileName = fileName == null ? "documento" : fileName;
        extractedText = extractedText == null ? "" : extractedText;
    }

    public boolean hasText() {
        return !extractedText.isBlank();
    }
}
