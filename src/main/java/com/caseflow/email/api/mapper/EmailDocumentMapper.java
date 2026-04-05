package com.caseflow.email.api.mapper;

import com.caseflow.email.api.dto.EmailDocumentRawResponse;
import com.caseflow.email.api.dto.EmailDocumentResponse;
import com.caseflow.email.api.dto.EmailDocumentSummaryResponse;
import com.caseflow.email.document.EmailDocument;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import java.util.List;

@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.ERROR,
        unmappedSourcePolicy = ReportingPolicy.IGNORE
)
public interface EmailDocumentMapper {

    /**
     * Operator-facing detail mapping. Exposes {@code sanitizedHtmlBody} — never raw HTML.
     * {@code attachments} is populated by the controller after fetching JPA records, so it is
     * ignored here to avoid mapping the MongoDB-embedded attachment list (which has no DB id).
     */
    @Mapping(target = "attachments", ignore = true)
    EmailDocumentResponse toResponse(EmailDocument document);

    /**
     * Raw/debug mapping. Exposes the original unsanitized {@code htmlBody}.
     * Restricted to admin/audit endpoints only — never used as the default view.
     */
    EmailDocumentRawResponse toRawResponse(EmailDocument document);

    /**
     * Lightweight summary — body and attachments omitted by design.
     */
    EmailDocumentSummaryResponse toSummaryResponse(EmailDocument document);

    List<EmailDocumentSummaryResponse> toSummaryResponseList(List<EmailDocument> documents);
}
