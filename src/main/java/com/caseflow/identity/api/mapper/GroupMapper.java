package com.caseflow.identity.api.mapper;

import com.caseflow.identity.api.dto.GroupResponse;
import com.caseflow.identity.api.dto.GroupSummaryResponse;
import com.caseflow.identity.domain.Group;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(
        componentModel = "spring",
        uses = {UserMapper.class},
        unmappedTargetPolicy = ReportingPolicy.ERROR,
        unmappedSourcePolicy = ReportingPolicy.IGNORE
)
public interface GroupMapper {

    // ── Entity → Response ─────────────────────────────────────────────────────

    /**
     * Full detail response including member list and count.
     * Requires group.users to be initialized (use findByIdWithMembers or @EntityGraph queries).
     */
    @Mapping(target = "groupTypeId",   expression = "java(group.getGroupType().getId())")
    @Mapping(target = "groupTypeCode", expression = "java(group.getGroupType().getCode())")
    @Mapping(target = "groupTypeName", expression = "java(group.getGroupType().getName())")
    @Mapping(target = "memberCount",   expression = "java(group.getUsers().size())")
    @Mapping(target = "members",       source = "users")
    GroupResponse toResponse(Group group);

    /**
     * Lightweight summary including memberCount and memberIds but not full member objects.
     * Requires group.users to be initialized (use @EntityGraph queries).
     */
    @Mapping(target = "groupTypeId",   expression = "java(group.getGroupType().getId())")
    @Mapping(target = "groupTypeCode", expression = "java(group.getGroupType().getCode())")
    @Mapping(target = "groupTypeName", expression = "java(group.getGroupType().getName())")
    @Mapping(target = "memberCount",   expression = "java(group.getUsers().size())")
    @Mapping(target = "memberIds",     expression = "java(group.getUsers().stream().map(com.caseflow.identity.domain.User::getId).toList())")
    GroupSummaryResponse toSummaryResponse(Group group);
}
