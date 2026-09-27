package com.caseflow.common.exception;

public class KnowledgeDocumentNotFoundException extends RuntimeException {

    public KnowledgeDocumentNotFoundException(Long id) {
        super("Knowledge document not found: " + id);
    }
}
