package com.caseflow.ticket.repository;

import com.caseflow.ticket.domain.AttachmentMetadata;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AttachmentMetadataRepository extends JpaRepository<AttachmentMetadata, Long> {

    List<AttachmentMetadata> findByTicketId(Long ticketId);

    List<AttachmentMetadata> findByEmailId(String emailId);

    Optional<AttachmentMetadata> findByObjectKey(String objectKey);

    Optional<AttachmentMetadata> findByIngressEventIdAndFileName(Long ingressEventId, String fileName);
}
