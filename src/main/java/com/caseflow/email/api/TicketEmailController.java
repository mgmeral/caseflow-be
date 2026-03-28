package com.caseflow.email.api;

import com.caseflow.auth.CaseFlowUserDetails;
import com.caseflow.email.api.dto.DispatchResponse;
import com.caseflow.email.api.dto.IngressEventResponse;
import com.caseflow.email.api.dto.SendReplyRequest;
import com.caseflow.email.api.mapper.DispatchMapper;
import com.caseflow.email.api.mapper.IngressEventMapper;
import com.caseflow.email.service.EmailDispatchService;
import com.caseflow.email.service.EmailIngressEventQueryService;
import com.caseflow.email.service.EmailMailboxService;
import com.caseflow.email.service.EmailReplyService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Ticket Email", description = "Email history and outbound replies for tickets")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/tickets/{ticketId}/email")
public class TicketEmailController {

    private static final Logger log = LoggerFactory.getLogger(TicketEmailController.class);

    private final EmailIngressEventQueryService ingressQueryService;
    private final EmailDispatchService dispatchService;
    private final EmailReplyService replyService;
    private final EmailMailboxService mailboxService;
    private final IngressEventMapper ingressMapper;
    private final DispatchMapper dispatchMapper;

    public TicketEmailController(EmailIngressEventQueryService ingressQueryService,
                                  EmailDispatchService dispatchService,
                                  EmailReplyService replyService,
                                  EmailMailboxService mailboxService,
                                  IngressEventMapper ingressMapper,
                                  DispatchMapper dispatchMapper) {
        this.ingressQueryService = ingressQueryService;
        this.dispatchService = dispatchService;
        this.replyService = replyService;
        this.mailboxService = mailboxService;
        this.ingressMapper = ingressMapper;
        this.dispatchMapper = dispatchMapper;
    }

    @GetMapping("/inbound")
    @PreAuthorize("@ticketAuth.canReadTicket(authentication, #ticketId)")
    public ResponseEntity<List<IngressEventResponse>> getInboundEvents(@PathVariable Long ticketId) {
        log.info("GET /tickets/{}/email/inbound", ticketId);
        return ResponseEntity.ok(
                ingressMapper.toResponseList(ingressQueryService.findByTicketId(ticketId)));
    }

    @GetMapping("/dispatches")
    @PreAuthorize("@ticketAuth.canReadTicket(authentication, #ticketId)")
    public ResponseEntity<List<DispatchResponse>> getDispatches(@PathVariable Long ticketId) {
        log.info("GET /tickets/{}/email/dispatches", ticketId);
        return ResponseEntity.ok(
                dispatchMapper.toResponseList(dispatchService.findByTicketId(ticketId)));
    }

    @PostMapping("/reply")
    @PreAuthorize("@ticketAuth.canSendCustomerReply(authentication, #ticketId)")
    public ResponseEntity<Void> sendReply(@PathVariable Long ticketId,
                                           @Valid @RequestBody SendReplyRequest request,
                                           @AuthenticationPrincipal CaseFlowUserDetails principal) {
        log.info("POST /tickets/{}/email/reply — to: '{}', mailboxId: {}",
                ticketId, request.toAddress(), request.mailboxId());
        String fromAddress = mailboxService.getById(request.mailboxId()).getAddress();
        replyService.sendReply(
                ticketId,
                fromAddress,
                request.toAddress(),
                request.subject(),
                request.textBody(),
                request.htmlBody(),
                request.inReplyToMessageId(),
                principal.getUserId()
        );
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }
}
