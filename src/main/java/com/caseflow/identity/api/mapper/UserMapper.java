package com.caseflow.identity.api.mapper;

import com.caseflow.identity.api.dto.UserResponse;
import com.caseflow.identity.api.dto.UserSummaryResponse;
import com.caseflow.identity.domain.Group;
import com.caseflow.identity.domain.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.ERROR,
        unmappedSourcePolicy = ReportingPolicy.IGNORE
)
public interface UserMapper {

    @Mapping(target = "roleId",
             expression = "java(user.getRole() != null ? user.getRole().getId() : null)")
    @Mapping(target = "roleCode",
             expression = "java(user.getRole() != null ? user.getRole().getCode() : null)")
    @Mapping(target = "roleName",
             expression = "java(user.getRole() != null ? user.getRole().getName() : null)")
    @Mapping(target = "groupIds",
             expression = "java(user.getGroups().stream().map(com.caseflow.identity.domain.Group::getId).toList())")
    @Mapping(target = "groupNames",
             expression = "java(user.getGroups().stream().map(com.caseflow.identity.domain.Group::getName).toList())")
    UserResponse toResponse(User user);

    @Mapping(target = "roleId",
             expression = "java(user.getRole() != null ? user.getRole().getId() : null)")
    @Mapping(target = "roleCode",
             expression = "java(user.getRole() != null ? user.getRole().getCode() : null)")
    UserSummaryResponse toSummaryResponse(User user);
}
