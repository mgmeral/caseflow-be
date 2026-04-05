package com.caseflow.storage.service;

import com.caseflow.common.exception.AttachmentNotFoundException;
import com.caseflow.storage.AttachmentKeyStrategy;
import com.caseflow.storage.ObjectStorageService;
import com.caseflow.ticket.domain.AttachmentMetadata;
import com.caseflow.ticket.domain.AttachmentSourceType;
import com.caseflow.ticket.repository.AttachmentMetadataRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class AttachmentService {

    private static final Logger log = LoggerFactory.getLogger(AttachmentService.class);

    private final AttachmentMetadataRepository attachmentMetadataRepository;
    private final ObjectStorageService objectStorageService;
    private final AttachmentKeyStrategy attachmentKeyStrategy;

    public AttachmentService(AttachmentMetadataRepository attachmentMetadataRepository,
                             ObjectStorageService objectStorageService,
                             AttachmentKeyStrategy attachmentKeyStrategy) {
        this.attachmentMetadataRepository = attachmentMetadataRepository;
        this.objectStorageService = objectStorageService;
        this.attachmentKeyStrategy = attachmentKeyStrategy;
    }

    @Transactional
    public AttachmentMetadata saveMetadata(Long ticketId, String emailId, String fileName,
                                           String objectKey, String contentType, Long size) {
        return saveMetadata(ticketId, emailId, fileName, objectKey, contentType, size, AttachmentSourceType.UPLOAD);
    }

    @Transactional
    public AttachmentMetadata saveMetadata(Long ticketId, String emailId, String fileName,
                                           String objectKey, String contentType, Long size,
                                           AttachmentSourceType sourceType) {
        AttachmentMetadata metadata = new AttachmentMetadata();
        metadata.setTicketId(ticketId);
        metadata.setEmailId(emailId);
        metadata.setFileName(fileName);
        metadata.setObjectKey(objectKey);
        metadata.setContentType(contentType);
        metadata.setSize(size);
        metadata.setSourceType(sourceType);
        return attachmentMetadataRepository.save(metadata);
    }

    /**
     * Saves attachment metadata for an email ingest without uploading binary data
     * (the binary is already in object storage from the email processing pipeline).
     *
     * <p><b>Idempotent:</b> if a record for {@code objectKey} already exists (inserted during a
     * previous processing attempt), the existing record is returned without a second INSERT.
     * This prevents duplicate-key constraint violations on retry, which would otherwise mark the
     * enclosing transaction as rollback-only and trigger the duplicate-ticket creation cycle.
     */
    @Transactional
    public AttachmentMetadata saveEmailAttachment(Long ticketId, String emailId, String fileName,
                                                   String objectKey, String contentType, Long size) {
        return attachmentMetadataRepository.findByObjectKey(objectKey)
                .orElseGet(() -> saveMetadata(ticketId, emailId, fileName, objectKey, contentType, size,
                        AttachmentSourceType.EMAIL_INBOUND));
    }

    /**
     * Saves attachment metadata for an IMAP-ingested email with full context.
     *
     * <p><b>Idempotent:</b> if a record already exists for the same {@code ingressEventId} and
     * {@code fileName} the existing record is returned unchanged. This prevents duplicate-key
     * constraint violations on event retry, which would otherwise mark the enclosing
     * {@code REQUIRES_NEW} transaction as rollback-only and keep the event in FAILED state forever.
     *
     * <p>The record is stored with {@code storageStage = "STAGING"} because the object key is still
     * the staging path. Call {@link #promoteAttachmentToFinalKey} immediately after to copy the
     * object to its canonical final path and update {@code storageStage} to {@code "FINAL"}.
     *
     * @param ticketId        resolved ticket id
     * @param ticketPublicId  resolved ticket public UUID
     * @param emailId         MongoDB EmailDocument id
     * @param ingressEventId  source ingress event id (idempotency key, together with fileName)
     * @param fileName        original file name
     * @param stagingObjectKey object key at which the IMAP poller stored the binary
     * @param contentType     MIME type
     * @param size            byte size
     */
    @Transactional
    public AttachmentMetadata saveEmailAttachmentWithContext(
            Long ticketId, UUID ticketPublicId, String emailId, Long ingressEventId,
            String fileName, String stagingObjectKey, String contentType, Long size) {
        return attachmentMetadataRepository
                .findByIngressEventIdAndFileName(ingressEventId, fileName)
                .orElseGet(() -> {
                    AttachmentMetadata metadata = new AttachmentMetadata();
                    metadata.setTicketId(ticketId);
                    metadata.setTicketPublicId(ticketPublicId);
                    metadata.setEmailId(emailId);
                    metadata.setIngressEventId(ingressEventId);
                    metadata.setFileName(fileName);
                    metadata.setObjectKey(stagingObjectKey);
                    metadata.setContentType(contentType);
                    metadata.setSize(size);
                    metadata.setSourceType(AttachmentSourceType.EMAIL_INBOUND);
                    metadata.setStorageStage("STAGING");
                    return attachmentMetadataRepository.save(metadata);
                });
    }

    /**
     * Promotes an attachment from its staging key to the deterministic final key, then updates
     * the metadata record. Idempotent: if the record already has {@code storageStage = "FINAL"}
     * this method is a no-op.
     *
     * <p>The final key is {@code tickets/{ticketPublicId}/emails/{emailDocumentId}/attachments/{id}/{fileName}}
     * — fully deterministic given the same inputs, so it is safe to recompute on retry.
     *
     * @param metadata        the metadata record to promote (must have a DB id assigned)
     * @param ticketPublicId  ticket public UUID used to build the final key path
     * @param emailDocumentId MongoDB EmailDocument id used to build the final key path
     */
    @Transactional
    public void promoteAttachmentToFinalKey(AttachmentMetadata metadata,
                                            UUID ticketPublicId, String emailDocumentId) {
        if ("FINAL".equals(metadata.getStorageStage())) return;
        String finalKey = attachmentKeyStrategy.finalKey(
                ticketPublicId, emailDocumentId, metadata.getId(), metadata.getFileName());
        objectStorageService.copy(metadata.getObjectKey(), finalKey);
        metadata.setObjectKey(finalKey);
        metadata.setStorageStage("FINAL");
        attachmentMetadataRepository.save(metadata);
        log.debug("Promoted attachment {} → {}", metadata.getId(), finalKey);
    }

    @Transactional(readOnly = true)
    public List<AttachmentMetadata> findByTicketId(Long ticketId) {
        return attachmentMetadataRepository.findByTicketId(ticketId);
    }

    @Transactional(readOnly = true)
    public List<AttachmentMetadata> findByEmailId(String emailId) {
        return attachmentMetadataRepository.findByEmailId(emailId);
    }

    @Transactional(readOnly = true)
    public Optional<AttachmentMetadata> findByObjectKey(String objectKey) {
        return attachmentMetadataRepository.findByObjectKey(objectKey);
    }

    @Transactional(readOnly = true)
    public AttachmentMetadata getByObjectKey(String objectKey) {
        return attachmentMetadataRepository.findByObjectKey(objectKey)
                .orElseThrow(() -> new AttachmentNotFoundException(objectKey));
    }

    /**
     * Store binary content and persist metadata in one call (legacy path — no publicId).
     */
    @Transactional
    public AttachmentMetadata upload(Long ticketId, String emailId, String fileName,
                                     String objectKey, String contentType, byte[] data) {
        log.info("Storing attachment — objectKey: '{}', ticketId: {}, size: {} bytes", objectKey, ticketId, data.length);
        objectStorageService.store(objectKey, data, contentType);
        AttachmentMetadata saved = saveMetadata(ticketId, emailId, fileName, objectKey, contentType, (long) data.length);
        log.info("Attachment stored — attachmentId: {}, objectKey: '{}'", saved.getId(), objectKey);
        return saved;
    }

    /**
     * Store binary content and persist metadata including the stable {@code ticketPublicId}.
     * Preferred over {@link #upload} for direct-upload requests.
     */
    @Transactional
    public AttachmentMetadata uploadWithPublicId(Long ticketId, java.util.UUID ticketPublicId,
                                                  String emailId, String fileName,
                                                  String objectKey, String contentType, byte[] data) {
        log.info("Storing attachment — objectKey: '{}', ticketId: {}, publicId: {}, size: {} bytes",
                objectKey, ticketId, ticketPublicId, data.length);
        objectStorageService.store(objectKey, data, contentType);
        AttachmentMetadata metadata = new AttachmentMetadata();
        metadata.setTicketId(ticketId);
        metadata.setTicketPublicId(ticketPublicId);
        metadata.setEmailId(emailId);
        metadata.setFileName(fileName);
        metadata.setObjectKey(objectKey);
        metadata.setContentType(contentType);
        metadata.setSize((long) data.length);
        metadata.setSourceType(com.caseflow.ticket.domain.AttachmentSourceType.UPLOAD);
        metadata.setStorageStage("FINAL");
        AttachmentMetadata saved = attachmentMetadataRepository.save(metadata);
        log.info("Attachment stored — attachmentId: {}, objectKey: '{}'", saved.getId(), objectKey);
        return saved;
    }

    @Transactional(readOnly = true)
    public AttachmentMetadata getById(Long id) {
        return attachmentMetadataRepository.findById(id)
                .orElseThrow(() -> new AttachmentNotFoundException("id=" + id));
    }

    /**
     * Retrieve binary content from storage.
     */
    @Transactional(readOnly = true)
    public InputStream download(String objectKey) {
        getByObjectKey(objectKey);
        return objectStorageService.retrieve(objectKey);
    }

    @Transactional
    public void delete(Long id) {
        AttachmentMetadata metadata = getById(id);
        log.info("Deleting attachment — attachmentId: {}, objectKey: '{}'", id, metadata.getObjectKey());
        objectStorageService.delete(metadata.getObjectKey());
        attachmentMetadataRepository.delete(metadata);
        log.info("Attachment {} deleted", id);
    }
}
