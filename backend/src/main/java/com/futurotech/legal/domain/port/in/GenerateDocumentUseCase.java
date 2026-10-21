package com.futurotech.legal.domain.port.in;

import com.futurotech.legal.domain.model.GeneratedDocument;
import java.util.List;
import java.util.Map;

/** Puerto de entrada (driving): orquesta la generación de un documento jurídico. */
public interface GenerateDocumentUseCase {

    record GenerateCommand(
            String templateId,
            String tenantId,
            String authorUserId,
            String entityId,
            Map<String, String> caseFields,
            List<String> selectedRules,
            boolean ragStrict
    ) {
    }

    /** Genera el documento, lo exporta y registra la auditoría. Devuelve el registro DRAFT. */
    GeneratedDocument generate(GenerateCommand command);
}
