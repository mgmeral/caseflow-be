package com.caseflow.email.api.mapper;

import com.caseflow.email.api.dto.EmailAttachmentResponse;
import com.caseflow.email.api.dto.EmailDocumentResponse;
import com.caseflow.email.api.dto.EmailDocumentSummaryResponse;
import com.caseflow.email.document.EmailDocument;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

import java.util.List;

@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.ERROR,
        unmappedSourcePolicy = ReportingPolicy.IGNORE
)
public interface EmailDocumentMapper {

    /**
     * Full detail mapping. Includes body content and attachment metadata.
     * Internal-only fields (inReplyTo, references, normalizedSubject, bcc)
     * are silently dropped via unmappedSourcePolicy = IGNORE.
     */
    EmailDocumentResponse toResponse(EmailDocument document);

    EmailAttachmentResponse toAttachmentResponse(EmailDocument.AttachmentMetadata metadata);

    /**
     * Lightweight summary — body and attachments omitted by design.
     */
    EmailDocumentSummaryResponse toSummaryResponse(EmailDocument document);

    List<EmailDocumentSummaryResponse> toSummaryResponseList(List<EmailDocument> documents);
}
