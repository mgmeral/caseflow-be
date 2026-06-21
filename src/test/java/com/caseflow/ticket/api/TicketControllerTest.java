package com.caseflow.ticket.api;

import com.caseflow.auth.CaseFlowUserDetailsService;
import com.caseflow.auth.JwtTokenService;
import com.caseflow.common.exception.TicketNotFoundException;
import com.caseflow.common.security.SecurityConfig;
import com.caseflow.email.service.EmailMailboxService;
import com.caseflow.email.service.EmailReplyService;
import com.caseflow.notification.service.NotificationService;
import com.caseflow.ticket.api.dto.CreateTicketRequest;
import com.caseflow.ticket.api.dto.TicketResponse;
import com.caseflow.ticket.api.dto.TicketSummaryResponse;
import com.caseflow.ticket.domain.Ticket;
import com.caseflow.ticket.domain.TicketPriority;
import com.caseflow.ticket.domain.TicketStatus;
import com.caseflow.ticket.security.TicketAuthorizationService;
import com.caseflow.ticket.service.TicketQueryService;
import com.caseflow.ticket.service.TicketReadService;
import com.caseflow.ticket.service.TicketService;
import com.caseflow.workflow.state.TicketStateMachineService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TicketController.class)
@Import(SecurityConfig.class)
class TicketControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private JwtTokenService jwtTokenService;

    @MockBean
    private CaseFlowUserDetailsService userDetailsService;

    @MockBean
    private TicketService ticketService;

    @MockBean
    private TicketReadService ticketReadService;

    @MockBean
    private TicketQueryService ticketQueryService;

    @MockBean
    private TicketStateMachineService stateMachine;

    @MockBean(name = "ticketAuth")
    private TicketAuthorizationService ticketAuth;

    @MockBean
    private NotificationService notificationService;

    @MockBean
    private EmailReplyService emailReplyService;

    @MockBean
    private EmailMailboxService mailboxService;

    @BeforeEach
    void allowAll() {
        when(ticketAuth.canReadTicket(any(Authentication.class), anyLong())).thenReturn(true);
        when(ticketAuth.canReadTicketByNo(any(Authentication.class), any())).thenReturn(true);
        when(ticketAuth.canChangePriority(any(Authentication.class), anyLong())).thenReturn(true);
        when(ticketAuth.canChangeTicketStatus(any(Authentication.class), anyLong())).thenReturn(true);
        when(ticketAuth.canCloseTicket(any(Authentication.class), anyLong())).thenReturn(true);
        when(ticketAuth.canViewAdminPool(any(Authentication.class))).thenReturn(true);
        when(ticketAuth.canSendCustomerReply(any(Authentication.class), anyLong())).thenReturn(true);
    }

    // ── GET /api/tickets/{id} ─────────────────────────────────────────────────

    @Test
    @WithMockUser(authorities = "PERM_TICKET_READ")
    void getById_returns200_withTicketResponse() throws Exception {
        TicketResponse response = makeTicketResponse(1L, "TKT-001");

        when(ticketReadService.getResponse(1L)).thenReturn(response);

        mockMvc.perform(get("/api/tickets/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.ticketNo").value("TKT-001"))
                .andExpect(jsonPath("$.status").value("NEW"));
    }

    @Test
    @WithMockUser(authorities = "PERM_TICKET_READ")
    void getById_returns404_whenTicketNotFound() throws Exception {
        when(ticketReadService.getResponse(999L)).thenThrow(new TicketNotFoundException(999L));

        mockMvc.perform(get("/api/tickets/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").exists());
    }

    @Test
    void getById_returns401_whenUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/tickets/1"))
                .andExpect(status().isUnauthorized());
    }

    // ── GET /api/tickets ──────────────────────────────────────────────────────

    @Test
    @WithMockUser(authorities = "PERM_TICKET_READ")
    void listTickets_returns200_withPagedResponse() throws Exception {
        TicketSummaryResponse summary = makeTicketSummary(1L, "TKT-001");

        when(ticketReadService.search(any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(summary)));

        mockMvc.perform(get("/api/tickets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].ticketNo").value("TKT-001"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @WithMockUser(authorities = "PERM_TICKET_READ")
    void listTickets_acceptsStatusChangedAtSort() throws Exception {
        // statusChangedAt is a supported sort field — must not fall back to createdAt
        TicketSummaryResponse summary = makeTicketSummary(1L, "TKT-001");
        when(ticketReadService.search(any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(summary)));

        mockMvc.perform(get("/api/tickets").param("sort", "statusChangedAt").param("direction", "asc"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "PERM_TICKET_READ")
    void listTickets_singleValueFilterContract_acceptsOneStatusValue() throws Exception {
        // Single-value filter contract: one status value must work cleanly
        TicketSummaryResponse summary = makeTicketSummary(1L, "TKT-001");
        when(ticketReadService.search(any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(summary)));

        mockMvc.perform(get("/api/tickets").param("status", "NEW"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @WithMockUser(authorities = "PERM_TICKET_READ")
    void listTickets_openOnlyFilter_returns200() throws Exception {
        TicketSummaryResponse summary = makeTicketSummary(1L, "TKT-001");
        when(ticketReadService.search(any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(summary)));

        mockMvc.perform(get("/api/tickets").param("openOnly", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @WithMockUser(authorities = "PERM_TICKET_READ")
    void listTickets_tagIdFilter_returns200() throws Exception {
        TicketSummaryResponse summary = makeTicketSummary(1L, "TKT-001");
        when(ticketReadService.search(any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(summary)));

        mockMvc.perform(get("/api/tickets").param("tagId", "5"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "PERM_TICKET_READ")
    void listTickets_tagCodeFilter_returns200() throws Exception {
        TicketSummaryResponse summary = makeTicketSummary(1L, "TKT-001");
        when(ticketReadService.search(any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(summary)));

        mockMvc.perform(get("/api/tickets").param("tagCode", "BUG"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "PERM_TICKET_READ")
    void listTickets_staleOpenOverHoursFilter_returns200() throws Exception {
        // staleOpenOverHours=24 is the drill-down filter for the dashboard waitingOver24h card
        TicketSummaryResponse summary = makeTicketSummary(1L, "TKT-001");
        when(ticketReadService.search(any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(summary)));

        mockMvc.perform(get("/api/tickets")
                        .param("staleOpenOverHours", "24"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @WithMockUser(authorities = "PERM_TICKET_READ")
    void listTickets_slaStateBreached_returns200() throws Exception {
        // slaState=BREACHED is the drill-down for the dashboard breachedSlaCount card
        TicketSummaryResponse summary = makeTicketSummary(1L, "TKT-001");
        when(ticketReadService.search(any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(summary)));

        mockMvc.perform(get("/api/tickets").param("slaState", "BREACHED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @WithMockUser(authorities = "PERM_TICKET_READ")
    void listTickets_slaStateAtRisk_returns200() throws Exception {
        // slaState=AT_RISK is the drill-down for the dashboard atRiskSlaCount card
        TicketSummaryResponse summary = makeTicketSummary(1L, "TKT-001");
        when(ticketReadService.search(any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(summary)));

        mockMvc.perform(get("/api/tickets").param("slaState", "AT_RISK"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    // ── POST /api/tickets ─────────────────────────────────────────────────────

    @Test
    @WithMockUser(authorities = "PERM_TICKET_STATUS_CHANGE")
    void createTicket_returns201_withValidRequest() throws Exception {
        CreateTicketRequest request = new CreateTicketRequest(
                "Login broken", "description", TicketPriority.HIGH, 1L
        );
        Ticket ticket = makeTicket(1L, "TKT-NEW");
        TicketResponse response = makeTicketResponse(1L, "TKT-NEW");

        when(ticketService.createTicket(any(), any(), any(), anyLong(), anyLong())).thenReturn(ticket);
        when(ticketReadService.getResponse(1L)).thenReturn(response);

        mockMvc.perform(post("/api/tickets")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ticketNo").value("TKT-NEW"));
    }

    @Test
    @WithMockUser(authorities = "PERM_TICKET_STATUS_CHANGE")
    void createTicket_returns400_whenSubjectIsBlank() throws Exception {
        CreateTicketRequest request = new CreateTicketRequest(
                "", null, TicketPriority.MEDIUM, null
        );

        mockMvc.perform(post("/api/tickets")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details").isArray());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Ticket makeTicket(Long id, String ticketNo) {
        Ticket t = new Ticket();
        t.setTicketNo(ticketNo);
        t.setSubject("Test");
        t.setStatus(TicketStatus.NEW);
        t.setPriority(TicketPriority.MEDIUM);
        try {
            var field = Ticket.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(t, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return t;
    }

    private TicketResponse makeTicketResponse(Long id, String ticketNo) {
        return new TicketResponse(id, null, ticketNo, "Test", null,
                TicketStatus.NEW, TicketPriority.MEDIUM,
                null, null, null, null, null, null,
                Instant.now(), Instant.now(), null, null);
    }

    private TicketSummaryResponse makeTicketSummary(Long id, String ticketNo) {
        return new TicketSummaryResponse(id, null, ticketNo, "Test",
                TicketStatus.NEW, TicketPriority.MEDIUM,
                null, null, null, null, null, null,
                Instant.now(), Instant.now(), null, null, null, null);
    }
}
