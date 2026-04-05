package com.caseflow.ticket.api.mapper;

import com.caseflow.ticket.api.dto.AttachmentMetadataResponse;
import com.caseflow.ticket.domain.AttachmentMetadata;
import com.caseflow.ticket.domain.AttachmentSourceType;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AttachmentMetadataMapperTest {

    private final AttachmentMetadataMapper mapper = Mappers.getMapper(AttachmentMetadataMapper.class);

    // ── downloadPath: ticket-scoped endpoint ─────────────────────────────────

    @Test
    void toResponse_usesTicketScopedEndpoint_whenTicketPublicIdAndEmailIdPresent() {
        UUID publicId = UUID.randomUUID();
        AttachmentMetadata metadata = metadataWithContext(7L, publicId, "doc-abc123");

        AttachmentMetadataResponse response = mapper.toResponse(metadata);

        assertThat(response.downloadPath())
                .isEqualTo("/api/tickets/" + publicId + "/emails/doc-abc123/attachments/7/content");
    }

    @Test
    void toResponse_fallsBackToLegacyPath_whenTicketPublicIdIsNull() {
        AttachmentMetadata metadata = metadataWithContext(5L, null, "doc-xyz");

        AttachmentMetadataResponse response = mapper.toResponse(metadata);

        assertThat(response.downloadPath()).isEqualTo("/api/attachments/5/download");
    }

    @Test
    void toResponse_fallsBackToLegacyPath_whenEmailIdIsNull() {
        UUID publicId = UUID.randomUUID();
        AttachmentMetadata metadata = metadataWithContext(3L, publicId, null);

        AttachmentMetadataResponse response = mapper.toResponse(metadata);

        assertThat(response.downloadPath()).isEqualTo("/api/attachments/3/download");
    }

    @Test
    void toResponse_fallsBackToLegacyPath_whenBothContextFieldsNull() {
        AttachmentMetadata metadata = metadataWithContext(2L, null, null);

        AttachmentMetadataResponse response = mapper.toResponse(metadata);

        assertThat(response.downloadPath()).isEqualTo("/api/attachments/2/download");
    }

    // ── downloadPath: raw bucket key is never exposed ────────────────────────

    @Test
    void toResponse_doesNotExposeRawObjectKey() {
        UUID publicId = UUID.randomUUID();
        AttachmentMetadata metadata = metadataWithContext(1L, publicId, "doc-1");
        // The raw objectKey contains a UUID-based filename segment — must never appear in downloadPath
        metadata.setObjectKey("tickets/" + publicId + "/emails/doc-1/attachments/abc-uuid_file.pdf");

        AttachmentMetadataResponse response = mapper.toResponse(metadata);

        // downloadPath is /api/tickets/… not the raw bucket key
        assertThat(response.downloadPath()).doesNotStartWith("tickets/");
        // The UUID-based filename component of the object key must not be in the URL
        assertThat(response.downloadPath()).doesNotContain("abc-uuid_file.pdf");
        // The downloadPath must not equal the raw key
        assertThat(response.downloadPath()).isNotEqualTo(metadata.getObjectKey());
    }

    // ── previewSupported ──────────────────────────────────────────────────────

    @Test
    void toResponse_previewSupported_trueForPdf() {
        AttachmentMetadata metadata = metadataWithContext(1L, null, null);
        metadata.setContentType("application/pdf");

        assertThat(mapper.toResponse(metadata).previewSupported()).isTrue();
    }

    @Test
    void toResponse_previewSupported_falseForZip() {
        AttachmentMetadata metadata = metadataWithContext(1L, null, null);
        metadata.setContentType("application/zip");

        assertThat(mapper.toResponse(metadata).previewSupported()).isFalse();
    }

    // ── toResponseList: same path resolution logic ────────────────────────────

    @Test
    void toResponseList_appliesScopedPath_whenContextPresent() {
        UUID publicId = UUID.randomUUID();
        List<AttachmentMetadata> list = List.of(
                metadataWithContext(10L, publicId, "doc-100"),
                metadataWithContext(11L, null, null)
        );

        List<AttachmentMetadataResponse> responses = mapper.toResponseList(list);

        assertThat(responses.get(0).downloadPath())
                .startsWith("/api/tickets/" + publicId + "/emails/doc-100/attachments/");
        assertThat(responses.get(1).downloadPath())
                .isEqualTo("/api/attachments/11/download");
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private AttachmentMetadata metadataWithContext(Long id, UUID ticketPublicId, String emailId) {
        AttachmentMetadata metadata = new AttachmentMetadata();
        try {
            var f = AttachmentMetadata.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(metadata, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        metadata.setTicketPublicId(ticketPublicId);
        metadata.setEmailId(emailId);
        metadata.setFileName("file.pdf");
        metadata.setContentType("application/pdf");
        metadata.setSize(1024L);
        metadata.setObjectKey("some/storage/key");
        metadata.setSourceType(AttachmentSourceType.EMAIL_INBOUND);
        return metadata;
    }
}
