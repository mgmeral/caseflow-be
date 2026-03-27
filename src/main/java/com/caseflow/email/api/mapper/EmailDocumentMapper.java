package com.caseflow.email.api.mapper;

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
     * Internal-only fields not present in the DTO (inReplyTo, references,
     * normalizedSubject, htmlBody, textBody, bcc, attachments) are silently
     * dropped via unmappedSourcePolicy = IGNORE.
     */
    EmailDocumentResponse toResponse(EmailDocument document);

    EmailDocumentSummaryResponse toSummaryResponse(EmailDocument document);

    List<EmailDocumentSummaryResponse> toSummaryResponseList(List<EmailDocument> documents);
}
