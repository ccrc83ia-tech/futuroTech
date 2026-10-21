package com.futurotech.legal.infrastructure.adapter.out.rag;

import com.futurotech.legal.domain.port.out.KnowledgeBasePort;
import com.futurotech.legal.infrastructure.adapter.out.ai.OllamaEmbeddingClient;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Base de conocimiento RAG sobre PostgreSQL + pgvector.
 * Almacena chunks en template_chunks y busca por similitud coseno.
 * Reemplaza al NoopKnowledgeBase (bean del mismo tipo).
 */
@Component
public class PgVectorKnowledgeBase implements KnowledgeBasePort {

    private static final Logger log = LoggerFactory.getLogger(PgVectorKnowledgeBase.class);

    private final JdbcTemplate jdbc;
    private final OllamaEmbeddingClient embeddingClient;
    private final int chunkSize;
    private final int chunkOverlap;

    public PgVectorKnowledgeBase(
            JdbcTemplate jdbc,
            OllamaEmbeddingClient embeddingClient,
            @Value("${rag.chunk-size:1500}") int chunkSize,
            @Value("${rag.chunk-overlap:200}") int chunkOverlap) {
        this.jdbc = jdbc;
        this.embeddingClient = embeddingClient;
        this.chunkSize = chunkSize;
        this.chunkOverlap = chunkOverlap;
    }

    @Override
    @Transactional
    public int ingest(String templateId, String docName, String text) {
        List<String> chunks = splitText(text, chunkSize, chunkOverlap);
        int index = 0;
        for (String chunk : chunks) {
            List<Float> embedding = embeddingClient.embed(chunk);
            jdbc.update(
                    "INSERT INTO template_chunks (template_id, doc_name, chunk_index, content, embedding) "
                    + "VALUES (?, ?, ?, ?, ?::vector)",
                    templateId, docName, index++, chunk, toVectorLiteral(embedding));
        }
        log.info("RAG ingest: {} chunks de '{}' en plantilla '{}'", chunks.size(), docName, templateId);
        return chunks.size();
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> search(String templateId, String query, int nResults) {
        if (!hasDocuments(templateId)) {
            return List.of();
        }
        List<Float> queryEmbedding = embeddingClient.embed(query);
        return jdbc.query(
                "SELECT content FROM template_chunks WHERE template_id = ? "
                + "ORDER BY embedding <=> ?::vector LIMIT ?",
                (rs, rowNum) -> rs.getString("content"),
                templateId, toVectorLiteral(queryEmbedding), nResults);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasDocuments(String templateId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM template_chunks WHERE template_id = ?", Integer.class, templateId);
        return count != null && count > 0;
    }

    @Override
    @Transactional
    public void deleteDocument(String templateId, String docName) {
        jdbc.update("DELETE FROM template_chunks WHERE template_id = ? AND doc_name = ?", templateId, docName);
    }

    @Override
    @Transactional
    public void deleteByTemplate(String templateId) {
        jdbc.update("DELETE FROM template_chunks WHERE template_id = ?", templateId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> listDocuments(String templateId) {
        return jdbc.queryForList(
                "SELECT DISTINCT doc_name FROM template_chunks WHERE template_id = ? ORDER BY doc_name",
                String.class, templateId);
    }

    /** Convierte el embedding a literal pgvector: [0.1,0.2,...]. */
    private static String toVectorLiteral(List<Float> v) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < v.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(v.get(i));
        }
        return sb.append(']').toString();
    }

    /** Fragmenta por párrafos respetando tamaño máximo y solapamiento. */
    public static List<String> splitText(String text, int chunkSize, int overlap) {
        List<String> paragraphs = new ArrayList<>();
        for (String p : text.split("\n")) {
            if (!p.isBlank()) {
                paragraphs.add(p.strip());
            }
        }
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String para : paragraphs) {
            if (current.length() + para.length() + 1 > chunkSize && current.length() > 0) {
                chunks.add(current.toString());
                String tail = overlap > 0 && current.length() > overlap
                        ? current.substring(current.length() - overlap)
                        : "";
                current = new StringBuilder(tail.isEmpty() ? para : tail + "\n" + para);
            } else {
                current.append(current.length() == 0 ? para : "\n" + para);
            }
        }
        if (current.length() > 0 && !current.toString().isBlank()) {
            chunks.add(current.toString());
        }
        return chunks;
    }
}
