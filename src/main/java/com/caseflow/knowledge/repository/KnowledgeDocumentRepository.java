package com.caseflow.knowledge.repository;

import com.caseflow.knowledge.domain.KnowledgeDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface KnowledgeDocumentRepository extends JpaRepository<KnowledgeDocument, Long> {

    Page<KnowledgeDocument> findAllByOrderByUpdatedAtDesc(Pageable pageable);

    List<KnowledgeDocument> findByIsActiveTrue();
}
