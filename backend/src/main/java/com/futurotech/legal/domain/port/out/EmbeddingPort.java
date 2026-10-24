package com.futurotech.legal.domain.port.out;

import java.util.List;

/**
 * Contrato agnóstico de embeddings: convierte texto en un vector denso para el
 * RAG. Implementado por el adaptador del proveedor activo (Gemini por defecto).
 * Desacopla la base de conocimiento ({@link KnowledgeBasePort}) del proveedor
 * concreto de embeddings.
 */
public interface EmbeddingPort {

    /** Devuelve el embedding del texto. La dimensión debe coincidir con el esquema vector(N). */
    List<Float> embed(String text);
}
