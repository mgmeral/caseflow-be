package com.caseflow.identity.api.mapper;

import com.caseflow.identity.api.dto.RoleResponse;
import com.caseflow.identity.api.dto.RoleSummaryResponse;
import com.caseflow.identity.domain.Permission;
import com.caseflow.identity.domain.Role;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.ERROR,
        unmappedSourcePolicy = ReportingPolicy.IGNORE
)
public interface RoleMapper {

    @Mapping(target = "permissionCodes",
             expression = "java(role.getPermissions().stream().map(com.caseflow.identity.domain.Permission::name).collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new)))")
    RoleResponse toResponse(Role role);

    @Mapping(target = "permissionCount",
             expression = "java(role.getPermissions().size())")
    RoleSummaryResponse toSummaryResponse(Role role);
}
