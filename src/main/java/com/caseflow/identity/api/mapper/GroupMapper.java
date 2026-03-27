package com.caseflow.identity.api.mapper;

import com.caseflow.identity.api.dto.CreateGroupRequest;
import com.caseflow.identity.api.dto.GroupResponse;
import com.caseflow.identity.api.dto.GroupSummaryResponse;
import com.caseflow.identity.api.dto.UpdateGroupRequest;
import com.caseflow.identity.domain.Group;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.ERROR,
        unmappedSourcePolicy = ReportingPolicy.IGNORE
)
public interface GroupMapper {

    // ── Entity → Response ─────────────────────────────────────────────────────

    GroupResponse toResponse(Group group);

    GroupSummaryResponse toSummaryResponse(Group group);

    // ── Request → Entity ──────────────────────────────────────────────────────

    /**
     * isActive is set to true by the service on creation.
     */
    @Mapping(target = "isActive", ignore = true)
    @Mapping(target = "users", ignore = true)
    Group toEntity(CreateGroupRequest request);

    /**
     * isActive is managed via activate/deactivate service calls.
     */
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "isActive", ignore = true)
    @Mapping(target = "users", ignore = true)
    void updateEntity(UpdateGroupRequest request, @MappingTarget Group group);
}
