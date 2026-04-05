package com.caseflow.storage.api;

import com.caseflow.auth.CaseFlowUserDetailsService;
import com.caseflow.auth.JwtTokenService;
import com.caseflow.common.exception.AttachmentNotFoundException;
import com.caseflow.common.security.SecurityConfig;
import com.caseflow.storage.AttachmentKeyStrategy;
import com.caseflow.storage.service.AttachmentService;
import com.caseflow.ticket.api.dto.AttachmentMetadataResponse;
import com.caseflow.ticket.api.mapper.AttachmentMetadataMapper;
import com.caseflow.ticket.domain.AttachmentMetadata;
import com.caseflow.ticket.domain.Ticket;
import com.caseflow.ticket.repository.TicketRepository;
import com.caseflow.ticket.security.TicketAuthorizationService;
import com.caseflow.workflow.history.TicketHistoryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.Authentication;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AttachmentController.class)
@Import(SecurityConfig.class)
class AttachmentControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private JwtTokenService jwtTokenService;
    @MockBean private CaseFlowUserDetailsService userDetailsService;
    @MockBean private AttachmentService attachmentService;
    @MockBean private AttachmentMetadataMapper attachmentMetadataMapper;
    @MockBean private AttachmentKeyStrategy keyStrategy;
    @MockBean private TicketRepository ticketRepository;
    @MockBean private TicketHistoryService historyService;
    @MockBean(name = "ticketAuth") private TicketAuthorizationService ticketAuth;

    // ── POST /api/attachments/upload ──────────────────────────────────────────

    @Test
    @WithMockUser
    void upload_returns201_withMetadataResponse() throws Exception {
        when(ticketAuth.canReadTicket(any(Authentication.class), anyLong())).thenReturn(true);

        Ticket ticket = new Ticket();
        try {
            var pid = Ticket.class.getDeclaredField("publicId");
            pid.setAccessible(true);
            pid.set(ticket, UUID.randomUUID());
            var id = Ticket.class.getDeclaredField("id");
            id.setAccessible(true);
            id.set(ticket, 10L);
        } catch (Exception e) { throw new RuntimeException(e); }

        when(ticketRepository.findById(10L)).thenReturn(Optional.of(ticket));
        when(keyStrategy.directUploadKey(any(UUID.class), anyString()))
                .thenReturn("tickets/uuid/attachments/uuid_report.pdf");

        AttachmentMetadata metadata = new AttachmentMetadata();
        AttachmentMetadataResponse response = makeMetadataResponse(1L, 10L, "report.pdf");

        when(attachmentService.uploadWithPublicId(anyLong(), any(UUID.class), any(), anyString(),
                anyString(), anyString(), any(byte[].class))).thenReturn(metadata);
        when(attachmentMetadataMapper.toResponse(any())).thenReturn(response);

        MockMultipartFile file = new MockMultipartFile(
                "file", "report.pdf", "application/pdf", "PDF content".getBytes());

        mockMvc.perform(multipart("/api/attachments/upload")
                        .file(file)
                        .param("ticketId", "10")
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.fileName").value("report.pdf"))
                .andExpect(jsonPath("$.ticketId").value(10));
    }

    @Test
    void upload_returns401_whenUnauthenticated() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "report.pdf", "application/pdf", "PDF content".getBytes());

        mockMvc.perform(multipart("/api/attachments/upload")
                        .file(file)
                        .param("ticketId", "10")
                        .with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void upload_returns403_whenNotAuthorized() throws Exception {
        when(ticketAuth.canReadTicket(any(Authentication.class), anyLong())).thenReturn(false);

        MockMultipartFile file = new MockMultipartFile(
                "file", "report.pdf", "application/pdf", "PDF content".getBytes());

        mockMvc.perform(multipart("/api/attachments/upload")
                        .file(file)
                        .param("ticketId", "10")
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    // ── GET /api/attachments/{id} ─────────────────────────────────────────────

    @Test
    @WithMockUser
    void getMetadata_returns200() throws Exception {
        when(ticketAuth.canReadAttachmentById(any(Authentication.class), anyLong())).thenReturn(true);

        AttachmentMetadataResponse response = makeMetadataResponse(1L, 10L, "report.pdf");
        when(attachmentService.getById(1L)).thenReturn(new AttachmentMetadata());
        when(attachmentMetadataMapper.toResponse(any())).thenReturn(response);

        mockMvc.perform(get("/api/attachments/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.fileName").value("report.pdf"));
    }

    @Test
    @WithMockUser
    void getMetadata_returns404_whenNotFound() throws Exception {
        when(ticketAuth.canReadAttachmentById(any(Authentication.class), anyLong())).thenReturn(true);
        when(attachmentService.getById(99L)).thenThrow(new AttachmentNotFoundException("99"));

        mockMvc.perform(get("/api/attachments/99"))
                .andExpect(status().isNotFound());
    }

    // ── GET /api/attachments/by-ticket/{ticketId} ─────────────────────────────

    @Test
    @WithMockUser
    void getByTicket_returns200_withList() throws Exception {
        when(ticketAuth.canReadTicket(any(Authentication.class), anyLong())).thenReturn(true);

        AttachmentMetadataResponse response = makeMetadataResponse(1L, 10L, "report.pdf");
        when(attachmentService.findByTicketId(10L)).thenReturn(List.of(new AttachmentMetadata()));
        when(attachmentMetadataMapper.toResponseList(any())).thenReturn(List.of(response));

        mockMvc.perform(get("/api/attachments/by-ticket/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].ticketId").value(10));
    }

    // ── GET /api/attachments/{id}/download ────────────────────────────────────

    @Test
    @WithMockUser
    void download_returns200_withFileContent() throws Exception {
        when(ticketAuth.canReadAttachmentById(any(Authentication.class), anyLong())).thenReturn(true);

        AttachmentMetadata meta = new AttachmentMetadata();
        meta.setFileName("report.pdf");
        meta.setObjectKey("tickets/10/uuid_report.pdf");
        meta.setContentType("application/pdf");
        meta.setSize(11L);

        when(attachmentService.getById(1L)).thenReturn(meta);
        when(attachmentService.download(anyString()))
                .thenReturn(new ByteArrayInputStream("PDF content".getBytes()));

        mockMvc.perform(get("/api/attachments/1/download"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"report.pdf\""))
                .andExpect(header().string("Content-Type", "application/pdf"));
    }

    // ── DELETE /api/attachments/{id} ──────────────────────────────────────────

    @Test
    @WithMockUser
    void delete_returns204() throws Exception {
        when(ticketAuth.canDeleteAttachmentById(any(Authentication.class), anyLong())).thenReturn(true);

        mockMvc.perform(delete("/api/attachments/1").with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser
    void delete_returns403_whenNotAuthorized() throws Exception {
        when(ticketAuth.canDeleteAttachmentById(any(Authentication.class), anyLong())).thenReturn(false);

        mockMvc.perform(delete("/api/attachments/1").with(csrf()))
                .andExpect(status().isForbidden());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private AttachmentMetadataResponse makeMetadataResponse(Long id, Long ticketId, String fileName) {
        return new AttachmentMetadataResponse(
                id, ticketId, null, null, fileName,
                "application/pdf", 1024L, "UPLOAD",
                "/api/attachments/" + id + "/download",
                false, Instant.now());
    }
}
