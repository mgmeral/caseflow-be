package com.caseflow.workflow.transfer.api;

import com.caseflow.common.security.SecurityContextHelper;
import com.caseflow.workflow.transfer.TransferService;
import com.caseflow.workflow.transfer.dto.TransferResponse;
import com.caseflow.workflow.transfer.dto.TransferSummaryResponse;
import com.caseflow.workflow.transfer.dto.TransferTicketRequest;
import com.caseflow.workflow.transfer.mapper.TransferMapper;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Transfers", description = "Ticket transfer workflow")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/transfers")
public class TransferController {

    private final TransferService transferService;
    private final TransferMapper transferMapper;

    public TransferController(TransferService transferService, TransferMapper transferMapper) {
        this.transferService = transferService;
        this.transferMapper = transferMapper;
    }

    @PostMapping
    public ResponseEntity<TransferResponse> transfer(@Valid @RequestBody TransferTicketRequest request) {
        Long userId = SecurityContextHelper.requireCurrentUserId();
        return ResponseEntity.status(HttpStatus.CREATED).body(
                transferMapper.toResponse(
                        transferService.transfer(
                                request.ticketId(), request.fromGroupId(), request.toGroupId(),
                                userId, request.reason(), request.clearAssignee()))
        );
    }

    @GetMapping("/by-ticket/{ticketId}")
    public ResponseEntity<List<TransferSummaryResponse>> getTransferHistory(@PathVariable Long ticketId) {
        return ResponseEntity.ok(
                transferMapper.toSummaryResponseList(transferService.getTransferHistory(ticketId)));
    }
}
