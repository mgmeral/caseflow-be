package com.caseflow.ticket.api.mapper;

import com.caseflow.ticket.api.dto.AttachmentMetadataResponse;
import com.caseflow.ticket.domain.AttachmentMetadata;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

import java.util.List;

@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.ERROR,
        unmappedSourcePolicy = ReportingPolicy.IGNORE
)
public interface AttachmentMetadataMapper {

    AttachmentMetadataResponse toResponse(AttachmentMetadata metadata);

    List<AttachmentMetadataResponse> toResponseList(List<AttachmentMetadata> metadata);
}
