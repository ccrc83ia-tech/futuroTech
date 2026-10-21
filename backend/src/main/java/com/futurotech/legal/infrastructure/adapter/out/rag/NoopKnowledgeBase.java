package com.futurotech.legal.infrastructure.adapter.out.rag;

import com.futurotech.legal.domain.port.out.KnowledgeBasePort;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Implementación por defecto de KnowledgeBasePort que no hace nada.
 * Se usa hasta que la Fase 4 provea el adaptador real de pgvector.
 * El adaptador real, al ser un bean del mismo tipo, lo sustituye.
 */
@Configuration
public class NoopKnowledgeBase {

    @Bean
    @ConditionalOnMissingBean(KnowledgeBasePort.class)
    public KnowledgeBasePort noopKnowledgeBase() {
        return new KnowledgeBasePort() {
            @Override
            public int ingest(String templateId, String docName, String text) {
                return 0;
            }

            @Override
            public List<String> search(String templateId, String query, int nResults) {
                return List.of();
            }

            @Override
            public boolean hasDocuments(String templateId) {
                return false;
            }

            @Override
            public void deleteDocument(String templateId, String docName) {
            }

            @Override
            public void deleteByTemplate(String templateId) {
            }

            @Override
            public List<String> listDocuments(String templateId) {
                return List.of();
            }
        };
    }
}
