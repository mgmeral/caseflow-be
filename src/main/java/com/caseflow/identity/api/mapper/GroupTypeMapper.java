package com.caseflow.identity.api.mapper;

import com.caseflow.identity.api.dto.GroupTypeResponse;
import com.caseflow.identity.api.dto.GroupTypeSummaryResponse;
import com.caseflow.identity.domain.GroupType;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.ERROR,
        unmappedSourcePolicy = ReportingPolicy.IGNORE
)
public interface GroupTypeMapper {

    GroupTypeResponse toResponse(GroupType groupType);

    GroupTypeSummaryResponse toSummaryResponse(GroupType groupType);
}
