package com.futurotech.legal.domain.port.in;

import java.util.List;
import java.util.Map;

import com.futurotech.legal.domain.model.CaseAttachment;
import com.futurotech.legal.domain.model.GeneratedDocument;

/** Puerto de entrada (driving): orquesta la generación de un documento jurídico. */
public interface GenerateDocumentUseCase {

    record GenerateCommand(
            String templateId,
            String tenantId,
            String authorUserId,
            String entityId,
            Map<String, String> caseFields,
            List<String> selectedRules,
            boolean ragStrict,
            List<CaseAttachment> caseAttachments
    ) {
        public GenerateCommand {
            caseAttachments = caseAttachments == null ? List.of() : List.copyOf(caseAttachments);
        }
    }

    /** Genera el documento, lo exporta y registra la auditoría. Devuelve el registro DRAFT. */
    GeneratedDocument generate(GenerateCommand command);
}
