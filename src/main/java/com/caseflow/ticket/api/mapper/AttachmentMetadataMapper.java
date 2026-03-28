package com.caseflow.ticket.api.mapper;

import com.caseflow.ticket.api.dto.AttachmentMetadataResponse;
import com.caseflow.ticket.domain.AttachmentMetadata;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import java.util.List;

@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.ERROR,
        unmappedSourcePolicy = ReportingPolicy.IGNORE
)
public interface AttachmentMetadataMapper {

    @Mapping(target = "downloadPath",
             expression = "java(\"/api/attachments/\" + metadata.getId() + \"/download\")")
    AttachmentMetadataResponse toResponse(AttachmentMetadata metadata);

    @Mapping(target = "downloadPath",
             expression = "java(\"/api/attachments/\" + metadata.getId() + \"/download\")")
    List<AttachmentMetadataResponse> toResponseList(List<AttachmentMetadata> metadata);
}
