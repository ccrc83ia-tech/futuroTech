package com.futurotech.legal.domain.model;

import java.util.List;

/**
 * Plantilla de documento jurídico. Incluye el prompt del sistema, reglas
 * jurídicas, campos configurables, modo RAG estricto y el documento modelo
 * (.docx) cuyo formato y estructura deben preservarse.
 */
public record Template(
        String templateId,
        String name,
        String description,
        List<String> fields,
        String systemPrompt,
        List<String> legalRules,
        boolean ragStrict,
        String formatDocName,
        byte[] formatDocBytes
) {
    public Template {
        fields = fields == null ? List.of() : List.copyOf(fields);
        legalRules = legalRules == null ? List.of() : List.copyOf(legalRules);
    }

    public boolean hasFormatDoc() {
        return formatDocBytes != null && formatDocBytes.length > 0;
    }
}
