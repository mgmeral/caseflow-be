package com.caseflow.ticket.api;

import com.caseflow.common.exception.TicketNotFoundException;
import com.caseflow.ticket.api.dto.CreateTicketRequest;
import com.caseflow.ticket.api.dto.TicketResponse;
import com.caseflow.ticket.api.dto.TicketSummaryResponse;
import com.caseflow.ticket.api.mapper.AttachmentMetadataMapper;
import com.caseflow.ticket.api.mapper.HistoryMapper;
import com.caseflow.ticket.api.mapper.TicketMapper;
import com.caseflow.ticket.domain.Ticket;
import com.caseflow.ticket.domain.TicketPriority;
import com.caseflow.ticket.domain.TicketStatus;
import com.caseflow.ticket.service.TicketQueryService;
import com.caseflow.ticket.service.TicketService;
import com.caseflow.common.security.SecurityConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
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
    private TicketService ticketService;

    @MockBean
    private TicketQueryService ticketQueryService;

    @MockBean
    private TicketMapper ticketMapper;

    @MockBean
    private AttachmentMetadataMapper attachmentMetadataMapper;

    @MockBean
    private HistoryMapper historyMapper;

    // ── GET /api/tickets/{id} ─────────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "VIEWER")
    void getById_returns200_withTicketResponse() throws Exception {
        Ticket ticket = makeTicket(1L, "TKT-001");
        TicketResponse response = makeTicketResponse(1L, "TKT-001");

        when(ticketQueryService.getById(1L)).thenReturn(ticket);
        when(ticketMapper.toResponse(ticket)).thenReturn(response);

        mockMvc.perform(get("/api/tickets/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.ticketNo").value("TKT-001"))
                .andExpect(jsonPath("$.status").value("NEW"));
    }

    @Test
    @WithMockUser(roles = "VIEWER")
    void getById_returns404_whenTicketNotFound() throws Exception {
        when(ticketQueryService.getById(999L)).thenThrow(new TicketNotFoundException(999L));

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
    @WithMockUser(roles = "VIEWER")
    void listTickets_returns200_withSummaryList() throws Exception {
        Ticket t = makeTicket(1L, "TKT-001");
        TicketSummaryResponse summary = makeTicketSummary(1L, "TKT-001");

        when(ticketQueryService.findAll()).thenReturn(List.of(t));
        when(ticketMapper.toSummaryResponse(t)).thenReturn(summary);

        mockMvc.perform(get("/api/tickets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].ticketNo").value("TKT-001"));
    }

    // ── POST /api/tickets ─────────────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "AGENT")
    void createTicket_returns201_withValidRequest() throws Exception {
        CreateTicketRequest request = new CreateTicketRequest(
                "Login broken", "description", TicketPriority.HIGH, 1L, 42L
        );
        Ticket ticket = makeTicket(1L, "TKT-NEW");
        TicketResponse response = makeTicketResponse(1L, "TKT-NEW");

        when(ticketService.createTicket(any(), any(), any(), anyLong(), anyLong())).thenReturn(ticket);
        when(ticketMapper.toResponse(ticket)).thenReturn(response);

        mockMvc.perform(post("/api/tickets")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ticketNo").value("TKT-NEW"));
    }

    @Test
    @WithMockUser(roles = "AGENT")
    void createTicket_returns400_whenSubjectIsBlank() throws Exception {
        CreateTicketRequest request = new CreateTicketRequest(
                "", null, TicketPriority.MEDIUM, null, 1L
        );

        mockMvc.perform(post("/api/tickets")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details").isArray());
    }

    @Test
    @WithMockUser(roles = "VIEWER")
    void createTicket_returns403_whenViewerRole() throws Exception {
        CreateTicketRequest request = new CreateTicketRequest(
                "Subject", null, TicketPriority.MEDIUM, null, 1L
        );

        mockMvc.perform(post("/api/tickets")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
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
        return new TicketResponse(id, ticketNo, "Test", null,
                TicketStatus.NEW, TicketPriority.MEDIUM, null, null, null,
                Instant.now(), Instant.now(), null);
    }

    private TicketSummaryResponse makeTicketSummary(Long id, String ticketNo) {
        return new TicketSummaryResponse(id, ticketNo, "Test",
                TicketStatus.NEW, TicketPriority.MEDIUM, null, null, Instant.now());
    }
}
