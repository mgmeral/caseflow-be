package com.caseflow.identity.api.mapper;

import com.caseflow.identity.api.dto.CreateUserRequest;
import com.caseflow.identity.api.dto.UpdateUserRequest;
import com.caseflow.identity.api.dto.UserResponse;
import com.caseflow.identity.api.dto.UserSummaryResponse;
import com.caseflow.identity.domain.User;
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
public interface UserMapper {

    // ── Entity → Response ─────────────────────────────────────────────────────

    UserResponse toResponse(User user);

    UserSummaryResponse toSummaryResponse(User user);

    // ── Request → Entity ──────────────────────────────────────────────────────

    /**
     * isActive is set to true by the service on creation.
     * lastLoginAt is managed by authentication infrastructure.
     */
    @Mapping(target = "isActive", ignore = true)
    @Mapping(target = "lastLoginAt", ignore = true)
    @Mapping(target = "groups", ignore = true)
    User toEntity(CreateUserRequest request);

    /**
     * Only email and fullName are updatable via this request.
     * username, isActive, and lastLoginAt are service-managed.
     */
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "username", ignore = true)
    @Mapping(target = "isActive", ignore = true)
    @Mapping(target = "lastLoginAt", ignore = true)
    @Mapping(target = "groups", ignore = true)
    void updateEntity(UpdateUserRequest request, @MappingTarget User user);
}
