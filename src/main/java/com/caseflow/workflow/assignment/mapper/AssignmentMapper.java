package com.caseflow.workflow.assignment.mapper;

import com.caseflow.workflow.assignment.dto.AssignmentResponse;
import com.caseflow.workflow.assignment.dto.AssignmentSummaryResponse;
import com.caseflow.workflow.domain.Assignment;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import java.util.List;

@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.ERROR,
        unmappedSourcePolicy = ReportingPolicy.IGNORE
)
public interface AssignmentMapper {

    /**
     * Assignment.isActive() — a computed boolean — maps to the 'active' record component.
     * Explicit declaration avoids reliance on MapStruct's is-prefix stripping heuristic.
     */
    @Mapping(target = "active", source = "active")
    AssignmentResponse toResponse(Assignment assignment);

    @Mapping(target = "active", source = "active")
    AssignmentSummaryResponse toSummaryResponse(Assignment assignment);

    List<AssignmentResponse> toResponseList(List<Assignment> assignments);
}
