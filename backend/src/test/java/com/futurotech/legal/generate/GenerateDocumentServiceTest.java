package com.futurotech.legal.generate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import com.futurotech.legal.application.service.GenerateDocumentService;
import com.futurotech.legal.domain.model.Branding;
import com.futurotech.legal.domain.model.DocumentStatus;
import com.futurotech.legal.domain.model.GeneratedDocument;
import com.futurotech.legal.domain.model.LegalContext;
import com.futurotech.legal.domain.model.Template;
import com.futurotech.legal.domain.model.TenantConfig;
import com.futurotech.legal.domain.port.in.GenerateDocumentUseCase.GenerateCommand;
import com.futurotech.legal.domain.port.out.AIServicePort;
import com.futurotech.legal.domain.port.out.FileExporterPort;
import com.futurotech.legal.domain.port.out.GeneratedDocumentPort;
import com.futurotech.legal.domain.port.out.KnowledgeBasePort;
import com.futurotech.legal.domain.port.out.TemplateRepositoryPort;
import com.futurotech.legal.domain.port.out.TenantRepositoryPort;

/**
 * Tests unitarios del orquestador de generación: composición del prompt,
 * comportamiento del modo RAG estricto y registro de auditoría en DRAFT.
 */
class GenerateDocumentServiceTest {

    private static final String TENANT = "firma-1";
    private static final String TEMPLATE = "PETICION-GENERAL-V1";

    private TenantRepositoryPort tenantRepo;
    private TemplateRepositoryPort templateRepo;
    private AIServicePort ai;
    private FileExporterPort fileExporter;
    private KnowledgeBasePort knowledgeBase;
    private GeneratedDocumentPort auditTrail;
    private GenerateDocumentService service;

    @BeforeEach
    void setUp() {
        tenantRepo = Mockito.mock(TenantRepositoryPort.class);
        templateRepo = Mockito.mock(TemplateRepositoryPort.class);
        ai = Mockito.mock(AIServicePort.class);
        fileExporter = Mockito.mock(FileExporterPort.class);
        knowledgeBase = Mockito.mock(KnowledgeBasePort.class);
        auditTrail = Mockito.mock(GeneratedDocumentPort.class);

        // Timeout corto para que la suite no se bloquee si algo va mal con el semáforo.
        service = new GenerateDocumentService(tenantRepo, templateRepo, ai, fileExporter,
                knowledgeBase, auditTrail, 1, 5);

        when(tenantRepo.findById(TENANT)).thenReturn(Optional.of(tenant()));
        when(templateRepo.findById(TEMPLATE)).thenReturn(Optional.of(template()));
        when(ai.generate(any(LegalContext.class))).thenReturn("Cuerpo del documento generado.");
        when(fileExporter.export(anyString(), any(), anyString(), any())).thenReturn("archivo.docx");
    }

    private static TenantConfig tenant() {
        return new TenantConfig(TENANT, "Firma Legal", "Prompt base de la firma.",
                List.of(), Branding.empty(), true);
    }

    private static Template template() {
        return new Template(TEMPLATE, "Petición general", "desc", List.of("cliente_nombre"),
                "Eres un abogado experto.", List.of("Artículo 23 CP"), true, null, null);
    }

    private GenerateCommand command(boolean ragStrict) {
        return new GenerateCommand(TEMPLATE, TENANT, "autor-1", null,
                Map.of("cliente_nombre", "Ana"), null, ragStrict, List.of());
    }

    private LegalContext captureContext() {
        var captor = ArgumentCaptor.forClass(LegalContext.class);
        Mockito.verify(ai).generate(captor.capture());
        return captor.getValue();
    }

    @Test
    void registersDraftAndCallsAi() {
        GeneratedDocument doc = service.generate(command(true));

        assertThat(doc.status()).isEqualTo(DocumentStatus.DRAFT);
        assertThat(doc.fileName()).isEqualTo("archivo.docx");
        assertThat(doc.fileHash()).isNotBlank();
        Mockito.verify(auditTrail).save(any(GeneratedDocument.class));
        Mockito.verify(ai).generate(any(LegalContext.class));
    }

    @Test
    void strictModeWithoutSourcesForbidsCitingText() {
        when(knowledgeBase.hasDocuments(TEMPLATE)).thenReturn(false);

        service.generate(command(true));

        String prompt = captureContext().systemPrompt();
        assertThat(prompt).contains("MODO ESTRICTO");
        assertThat(prompt).contains("[texto no disponible en fuentes autorizadas]");
    }

    @Test
    void strictModeWithSourcesInjectsAuthorizedSources() {
        when(knowledgeBase.hasDocuments(TEMPLATE)).thenReturn(true);
        when(knowledgeBase.search(eq(TEMPLATE), anyString(), anyInt()))
                .thenReturn(List.of("Artículo 23. Toda persona tiene derecho a presentar peticiones."));

        service.generate(command(true));

        String prompt = captureContext().systemPrompt();
        assertThat(prompt).contains("FUENTES NORMATIVAS AUTORIZADAS");
        assertThat(prompt).contains("derecho a presentar peticiones");
    }

    @Test
    void nonStrictModeAllowsComplementingWithOwnKnowledge() {
        when(knowledgeBase.hasDocuments(TEMPLATE)).thenReturn(true);
        when(knowledgeBase.search(eq(TEMPLATE), anyString(), anyInt()))
                .thenReturn(List.of("Fragmento de referencia."));

        service.generate(command(false));

        String prompt = captureContext().systemPrompt();
        assertThat(prompt).doesNotContain("FUENTES NORMATIVAS AUTORIZADAS");
        assertThat(prompt).contains("Documentos de referencia");
    }
}
