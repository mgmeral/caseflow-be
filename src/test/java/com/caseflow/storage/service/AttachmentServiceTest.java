package com.caseflow.storage.service;

import com.caseflow.storage.ObjectStorageService;
import com.caseflow.ticket.domain.AttachmentMetadata;
import com.caseflow.ticket.domain.AttachmentSourceType;
import com.caseflow.ticket.repository.AttachmentMetadataRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttachmentServiceTest {

    @Mock private AttachmentMetadataRepository attachmentMetadataRepository;
    @Mock private ObjectStorageService objectStorageService;

    @InjectMocks
    private AttachmentService attachmentService;

    @Test
    void saveEmailAttachment_isIdempotent_whenObjectKeyAlreadyExists() {
        AttachmentMetadata existing = new AttachmentMetadata();
        existing.setObjectKey("emails/inbox/uid-42/report.pdf");
        existing.setSourceType(AttachmentSourceType.EMAIL_INBOUND);

        when(attachmentMetadataRepository.findByObjectKey("emails/inbox/uid-42/report.pdf"))
                .thenReturn(Optional.of(existing));

        AttachmentMetadata result = attachmentService.saveEmailAttachment(
                10L, "doc-abc", "report.pdf",
                "emails/inbox/uid-42/report.pdf", "application/pdf", 4096L);

        assertSame(existing, result);
        verify(attachmentMetadataRepository, never()).save(any());
    }

    @Test
    void saveEmailAttachment_savesNewRecord_whenObjectKeyIsNew() {
        when(attachmentMetadataRepository.findByObjectKey("emails/inbox/uid-99/new.pdf"))
                .thenReturn(Optional.empty());

        AttachmentMetadata saved = new AttachmentMetadata();
        saved.setObjectKey("emails/inbox/uid-99/new.pdf");
        when(attachmentMetadataRepository.save(any())).thenReturn(saved);

        AttachmentMetadata result = attachmentService.saveEmailAttachment(
                10L, "doc-xyz", "new.pdf",
                "emails/inbox/uid-99/new.pdf", "application/pdf", 2048L);

        assertSame(saved, result);
        verify(attachmentMetadataRepository).save(any());
    }
}
