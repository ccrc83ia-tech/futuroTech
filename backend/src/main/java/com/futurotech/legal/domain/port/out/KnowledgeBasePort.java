package com.futurotech.legal.domain.port.out;

import java.util.List;

/** Base de conocimiento RAG: indexa y recupera fragmentos de documentos. */
public interface KnowledgeBasePort {

    /** Fragmenta e indexa texto para una plantilla. Devuelve nº de chunks creados. */
    int ingest(String templateId, String docName, String text);

    /** Devuelve los fragmentos más relevantes para una consulta. */
    List<String> search(String templateId, String query, int nResults);

    /** True si hay al menos un fragmento indexado para la plantilla. */
    boolean hasDocuments(String templateId);

    /** Elimina todos los fragmentos de un documento de una plantilla. */
    void deleteDocument(String templateId, String docName);

    /** Elimina todos los fragmentos de una plantilla. */
    void deleteByTemplate(String templateId);

    /** Lista los nombres de documentos indexados para una plantilla. */
    List<String> listDocuments(String templateId);
}
