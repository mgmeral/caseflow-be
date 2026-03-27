package com.caseflow.workflow.transfer.mapper;

import com.caseflow.workflow.domain.Transfer;
import com.caseflow.workflow.transfer.dto.TransferResponse;
import com.caseflow.workflow.transfer.dto.TransferSummaryResponse;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

import java.util.List;

@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.ERROR,
        unmappedSourcePolicy = ReportingPolicy.IGNORE
)
public interface TransferMapper {

    TransferResponse toResponse(Transfer transfer);

    TransferSummaryResponse toSummaryResponse(Transfer transfer);

    List<TransferSummaryResponse> toSummaryResponseList(List<Transfer> transfers);
}
