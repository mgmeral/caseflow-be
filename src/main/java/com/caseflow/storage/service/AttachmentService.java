package com.caseflow.storage.service;

import com.caseflow.common.exception.AttachmentNotFoundException;
import com.caseflow.storage.ObjectStorageService;
import com.caseflow.ticket.domain.AttachmentMetadata;
import com.caseflow.ticket.repository.AttachmentMetadataRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.util.List;
import java.util.Optional;

@Service
public class AttachmentService {

    private static final Logger log = LoggerFactory.getLogger(AttachmentService.class);

    private final AttachmentMetadataRepository attachmentMetadataRepository;
    private final ObjectStorageService objectStorageService;

    public AttachmentService(AttachmentMetadataRepository attachmentMetadataRepository,
                             ObjectStorageService objectStorageService) {
        this.attachmentMetadataRepository = attachmentMetadataRepository;
        this.objectStorageService = objectStorageService;
    }

    @Transactional
    public AttachmentMetadata saveMetadata(Long ticketId, String emailId, String fileName,
                                           String objectKey, String contentType, Long size) {
        AttachmentMetadata metadata = new AttachmentMetadata();
        metadata.setTicketId(ticketId);
        metadata.setEmailId(emailId);
        metadata.setFileName(fileName);
        metadata.setObjectKey(objectKey);
        metadata.setContentType(contentType);
        metadata.setSize(size);
        return attachmentMetadataRepository.save(metadata);
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
     * Store binary content and persist metadata in one call.
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
