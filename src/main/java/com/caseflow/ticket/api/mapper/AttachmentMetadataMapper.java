package com.caseflow.ticket.api.mapper;

import com.caseflow.ticket.api.dto.AttachmentMetadataResponse;
import com.caseflow.ticket.domain.AttachmentMetadata;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import java.util.List;
import java.util.Set;

@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.ERROR,
        unmappedSourcePolicy = ReportingPolicy.IGNORE
)
public interface AttachmentMetadataMapper {

    Set<String> PREVIEWABLE_TYPES = Set.of(
            "image/jpeg", "image/png", "image/gif", "image/webp",
            "application/pdf", "text/plain");

    @Mapping(target = "downloadPath",
             expression = "java(resolveDownloadPath(metadata))")
    @Mapping(target = "previewSupported",
             expression = "java(isPreviewable(metadata.getContentType()))")
    @Mapping(target = "sourceType",
             expression = "java(metadata.getSourceType() != null ? metadata.getSourceType().name() : null)")
    AttachmentMetadataResponse toResponse(AttachmentMetadata metadata);

    @Mapping(target = "downloadPath",
             expression = "java(resolveDownloadPath(metadata))")
    @Mapping(target = "previewSupported",
             expression = "java(isPreviewable(metadata.getContentType()))")
    @Mapping(target = "sourceType",
             expression = "java(metadata.getSourceType() != null ? metadata.getSourceType().name() : null)")
    List<AttachmentMetadataResponse> toResponseList(List<AttachmentMetadata> metadata);

    /**
     * Prefers the ticket-scoped content endpoint when {@code ticketPublicId} and {@code emailId}
     * are both present (email attachments migrated to FINAL storage stage).
     * Falls back to the legacy generic download endpoint for direct-upload or staging attachments.
     */
    default String resolveDownloadPath(AttachmentMetadata metadata) {
        if (metadata.getTicketPublicId() != null && metadata.getEmailId() != null) {
            return "/api/tickets/" + metadata.getTicketPublicId()
                    + "/emails/" + metadata.getEmailId()
                    + "/attachments/" + metadata.getId() + "/content";
        }
        return "/api/attachments/" + metadata.getId() + "/download";
    }

    default boolean isPreviewable(String contentType) {
        if (contentType == null) return false;
        return PREVIEWABLE_TYPES.contains(contentType.toLowerCase());
    }
}
