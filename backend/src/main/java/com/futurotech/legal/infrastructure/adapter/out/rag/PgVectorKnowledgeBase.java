package com.futurotech.legal.infrastructure.adapter.out.rag;

import com.futurotech.legal.domain.port.out.EmbeddingPort;
import com.futurotech.legal.domain.port.out.KnowledgeBasePort;
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

    /** Factor de sobre-recuperación para el reranking: pool = nResults * factor. */
    private static final int RERANK_POOL_FACTOR = 3;

    /** Inicio de un artículo/numeral jurídico: punto de corte natural de un chunk. */
    private static final java.util.regex.Pattern LEGAL_BOUNDARY = java.util.regex.Pattern.compile(
            "(?im)^\\s*(?:art[íi]culo|art\\.|par[áa]grafo|numeral)\\b.*");

    private final JdbcTemplate jdbc;
    private final EmbeddingPort embeddingClient;
    private final int chunkSize;
    private final int chunkOverlap;

    public PgVectorKnowledgeBase(
            JdbcTemplate jdbc,
            EmbeddingPort embeddingClient,
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
        // Recuperación en dos etapas: traer un pool amplio de candidatos por
        // similitud vectorial y reordenarlos con una señal léxica (solapamiento
        // de términos query-chunk). Mejora la precisión del top-N frente a usar
        // solo coseno, sin coste adicional de API. Ver rerank().
        int poolSize = Math.max(nResults, nResults * RERANK_POOL_FACTOR);
        List<String> candidates = jdbc.query(
                "SELECT content FROM template_chunks WHERE template_id = ? "
                + "ORDER BY embedding <=> ?::vector LIMIT ?",
                (rs, rowNum) -> rs.getString("content"),
                templateId, toVectorLiteral(queryEmbedding), poolSize);
        return rerank(query, candidates, nResults);
    }

    /**
     * Reordena los candidatos combinando su orden vectorial (ya vienen por
     * similitud) con una señal léxica: cuántos términos de la consulta aparecen
     * en el fragmento. Favorece fragmentos que mencionan explícitamente los
     * términos buscados (números de norma, nombres de entidad) sin descartar la
     * señal semántica. Devuelve los {@code n} mejores.
     */
    static List<String> rerank(String query, List<String> candidates, int n) {
        if (candidates.size() <= n) {
            return candidates;
        }
        List<String> queryTerms = tokenize(query);
        // Puntuación: posición vectorial (peso decreciente) + solapamiento léxico.
        List<ScoredChunk> scored = new ArrayList<>();
        for (int i = 0; i < candidates.size(); i++) {
            String chunk = candidates.get(i);
            double vectorScore = 1.0 - (double) i / candidates.size(); // 1.0 el mejor, decrece
            double lexical = lexicalOverlap(queryTerms, tokenize(chunk));
            double score = 0.6 * vectorScore + 0.4 * lexical;
            scored.add(new ScoredChunk(chunk, score));
        }
        scored.sort((a, b) -> Double.compare(b.score, a.score));
        List<String> result = new ArrayList<>();
        for (int i = 0; i < n && i < scored.size(); i++) {
            result.add(scored.get(i).chunk);
        }
        return result;
    }

    private static double lexicalOverlap(List<String> queryTerms, List<String> chunkTerms) {
        if (queryTerms.isEmpty()) {
            return 0.0;
        }
        java.util.Set<String> chunkSet = new java.util.HashSet<>(chunkTerms);
        long hits = queryTerms.stream().filter(chunkSet::contains).count();
        return (double) hits / queryTerms.size();
    }

    /** Tokeniza a minúsculas, descartando términos muy cortos (ruido). */
    private static List<String> tokenize(String text) {
        List<String> terms = new ArrayList<>();
        for (String t : text.toLowerCase().split("[^\\p{L}\\p{Nd}º°]+")) {
            if (t.length() >= 3) {
                terms.add(t);
            }
        }
        return terms;
    }

    private record ScoredChunk(String chunk, double score) {
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

    /**
     * Fragmenta por párrafos respetando tamaño máximo y solapamiento, y además
     * consciente de la estructura legal: si un párrafo inicia un artículo,
     * parágrafo o numeral y el chunk actual ya tiene contenido, cierra el chunk
     * para no partir una norma a la mitad. Esto mejora la precisión del RAG
     * jurídico (los límites de norma son fronteras semánticas naturales).
     */
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
            boolean legalBoundary = LEGAL_BOUNDARY.matcher(para).find();
            boolean exceedsSize = current.length() + para.length() + 1 > chunkSize;
            // Cerrar el chunk si: supera el tamaño, o empieza una nueva norma
            // (y el chunk ya tiene algo). En ambos casos arrastra el solapamiento.
            if ((exceedsSize || legalBoundary) && current.length() > 0) {
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
