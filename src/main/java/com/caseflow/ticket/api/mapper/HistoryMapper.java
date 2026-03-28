package com.caseflow.ticket.api.mapper;

import com.caseflow.ticket.api.dto.HistoryResponse;
import com.caseflow.ticket.api.dto.HistorySummaryResponse;
import com.caseflow.ticket.domain.History;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import java.util.List;

@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.ERROR,
        unmappedSourcePolicy = ReportingPolicy.IGNORE
)
public interface HistoryMapper {

    @Mapping(target = "performedByName", ignore = true)
    HistoryResponse toResponse(History history);

    @Mapping(target = "performedByName", ignore = true)
    HistorySummaryResponse toSummaryResponse(History history);

    @Mapping(target = "performedByName", ignore = true)
    List<HistorySummaryResponse> toSummaryResponseList(List<History> history);
}
