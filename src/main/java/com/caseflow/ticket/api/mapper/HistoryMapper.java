package com.caseflow.ticket.api.mapper;

import com.caseflow.ticket.api.dto.HistoryResponse;
import com.caseflow.ticket.api.dto.HistorySummaryResponse;
import com.caseflow.ticket.domain.History;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

import java.util.List;

@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.ERROR,
        unmappedSourcePolicy = ReportingPolicy.IGNORE
)
public interface HistoryMapper {

    HistoryResponse toResponse(History history);

    HistorySummaryResponse toSummaryResponse(History history);

    List<HistorySummaryResponse> toSummaryResponseList(List<History> history);
}
