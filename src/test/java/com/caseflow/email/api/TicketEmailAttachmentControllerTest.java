package com.caseflow.email.api;

import com.caseflow.auth.CaseFlowUserDetailsService;
import com.caseflow.auth.JwtTokenService;
import com.caseflow.common.security.SecurityConfig;
import com.caseflow.storage.service.AttachmentService;
import com.caseflow.ticket.api.dto.AttachmentMetadataResponse;
import com.caseflow.ticket.api.mapper.AttachmentMetadataMapper;
import com.caseflow.ticket.domain.AttachmentMetadata;
import com.caseflow.ticket.domain.Ticket;
import com.caseflow.ticket.security.TicketAuthorizationService;
import com.caseflow.ticket.service.TicketQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TicketEmailAttachmentController.class)
@Import(SecurityConfig.class)
class TicketEmailAttachmentControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private JwtTokenService jwtTokenService;
    @MockBean private CaseFlowUserDetailsService userDetailsService;
    @MockBean private AttachmentService attachmentService;
    @MockBean private AttachmentMetadataMapper attachmentMetadataMapper;
    @MockBean private TicketQueryService ticketQueryService;
    @MockBean(name = "ticketAuth") private TicketAuthorizationService ticketAuth;

    private static final UUID PUBLIC_ID = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
    private static final String EMAIL_ID = "mongo123";

    // ── GET /api/tickets/{publicId}/emails/{emailId}/attachments ─────────────

    @Test
    @WithMockUser(authorities = "PERM_TICKET_EMAIL_VIEW")
    void listAttachments_returns200_withMetadata() throws Exception {
        Ticket ticket = makeTicket(1L, PUBLIC_ID);
        AttachmentMetadata meta = makeMetadata(10L, 1L, EMAIL_ID, "report.pdf", "application/pdf");
        AttachmentMetadataResponse resp = makeResponse(10L, "report.pdf", "application/pdf", true);

        when(ticketAuth.canViewTicketEmailByPublicId(any(), any())).thenReturn(true);
        when(ticketQueryService.getByPublicId(PUBLIC_ID)).thenReturn(ticket);
        when(attachmentService.findByEmailId(EMAIL_ID)).thenReturn(List.of(meta));
        when(attachmentMetadataMapper.toResponseList(any())).thenReturn(List.of(resp));

        mockMvc.perform(get("/api/tickets/{publicId}/emails/{emailId}/attachments",
                        PUBLIC_ID, EMAIL_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].id").value(10))
                .andExpect(jsonPath("$[0].previewSupported").value(true));
    }

    @Test
    @WithMockUser(authorities = "PERM_TICKET_EMAIL_VIEW")
    void listAttachments_returns200_emptyList_whenNoAttachments() throws Exception {
        Ticket ticket = makeTicket(1L, PUBLIC_ID);

        when(ticketAuth.canViewTicketEmailByPublicId(any(), any())).thenReturn(true);
        when(ticketQueryService.getByPublicId(PUBLIC_ID)).thenReturn(ticket);
        when(attachmentService.findByEmailId(EMAIL_ID)).thenReturn(List.of());
        when(attachmentMetadataMapper.toResponseList(any())).thenReturn(List.of());

        mockMvc.perform(get("/api/tickets/{publicId}/emails/{emailId}/attachments",
                        PUBLIC_ID, EMAIL_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void listAttachments_returns401_whenUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/tickets/{publicId}/emails/{emailId}/attachments",
                        PUBLIC_ID, EMAIL_ID))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(authorities = "PERM_TICKET_EMAIL_VIEW")
    void listAttachments_filters_attachmentsBelongingToOtherTickets() throws Exception {
        Ticket ticket = makeTicket(1L, PUBLIC_ID);
        // This attachment belongs to a DIFFERENT ticket (id=2) — must be filtered out
        AttachmentMetadata otherTicket = makeMetadata(20L, 2L, EMAIL_ID, "other.pdf", "application/pdf");

        when(ticketAuth.canViewTicketEmailByPublicId(any(), any())).thenReturn(true);
        when(ticketQueryService.getByPublicId(PUBLIC_ID)).thenReturn(ticket);
        when(attachmentService.findByEmailId(EMAIL_ID)).thenReturn(List.of(otherTicket));
        when(attachmentMetadataMapper.toResponseList(List.of())).thenReturn(List.of());

        mockMvc.perform(get("/api/tickets/{publicId}/emails/{emailId}/attachments",
                        PUBLIC_ID, EMAIL_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    // ── GET /api/tickets/{publicId}/emails/{emailId}/attachments/{id}/content ─

    @Test
    @WithMockUser(authorities = "PERM_TICKET_EMAIL_VIEW")
    void getContent_returns200_andStreamsContent() throws Exception {
        Ticket ticket = makeTicket(1L, PUBLIC_ID);
        AttachmentMetadata meta = makeMetadata(10L, 1L, EMAIL_ID, "image.png", "image/png");

        when(ticketAuth.canViewTicketEmailByPublicId(any(), any())).thenReturn(true);
        when(ticketQueryService.getByPublicId(PUBLIC_ID)).thenReturn(ticket);
        when(attachmentService.getById(10L)).thenReturn(meta);
        when(attachmentService.download(anyString()))
                .thenReturn(new ByteArrayInputStream("PNG_BYTES".getBytes()));

        mockMvc.perform(get("/api/tickets/{publicId}/emails/{emailId}/attachments/{id}/content",
                        PUBLIC_ID, EMAIL_ID, 10L))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "image/png"))
                // Previewable type → inline
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"image.png\""));
    }

    @Test
    @WithMockUser(authorities = "PERM_TICKET_EMAIL_VIEW")
    void getContent_usesInlineDisposition_forPreviewableTypes() throws Exception {
        Ticket ticket = makeTicket(1L, PUBLIC_ID);
        AttachmentMetadata meta = makeMetadata(11L, 1L, EMAIL_ID, "doc.pdf", "application/pdf");

        when(ticketAuth.canViewTicketEmailByPublicId(any(), any())).thenReturn(true);
        when(ticketQueryService.getByPublicId(PUBLIC_ID)).thenReturn(ticket);
        when(attachmentService.getById(11L)).thenReturn(meta);
        when(attachmentService.download(anyString()))
                .thenReturn(new ByteArrayInputStream("PDF".getBytes()));

        mockMvc.perform(get("/api/tickets/{publicId}/emails/{emailId}/attachments/{id}/content",
                        PUBLIC_ID, EMAIL_ID, 11L))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"doc.pdf\""));
    }

    @Test
    @WithMockUser(authorities = "PERM_TICKET_EMAIL_VIEW")
    void getContent_usesAttachmentDisposition_forNonPreviewableTypes() throws Exception {
        Ticket ticket = makeTicket(1L, PUBLIC_ID);
        AttachmentMetadata meta = makeMetadata(12L, 1L, EMAIL_ID, "data.zip", "application/zip");

        when(ticketAuth.canViewTicketEmailByPublicId(any(), any())).thenReturn(true);
        when(ticketQueryService.getByPublicId(PUBLIC_ID)).thenReturn(ticket);
        when(attachmentService.getById(12L)).thenReturn(meta);
        when(attachmentService.download(anyString()))
                .thenReturn(new ByteArrayInputStream("ZIP".getBytes()));

        mockMvc.perform(get("/api/tickets/{publicId}/emails/{emailId}/attachments/{id}/content",
                        PUBLIC_ID, EMAIL_ID, 12L))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"data.zip\""));
    }

    @Test
    @WithMockUser(authorities = "PERM_TICKET_EMAIL_VIEW")
    void getContent_returns404_whenAttachmentBelongsToOtherTicket() throws Exception {
        Ticket ticket = makeTicket(1L, PUBLIC_ID);
        // Attachment belongs to ticketId=2, not 1
        AttachmentMetadata meta = makeMetadata(20L, 2L, EMAIL_ID, "evil.pdf", "application/pdf");

        when(ticketAuth.canViewTicketEmailByPublicId(any(), any())).thenReturn(true);
        when(ticketQueryService.getByPublicId(PUBLIC_ID)).thenReturn(ticket);
        when(attachmentService.getById(20L)).thenReturn(meta);

        mockMvc.perform(get("/api/tickets/{publicId}/emails/{emailId}/attachments/{id}/content",
                        PUBLIC_ID, EMAIL_ID, 20L))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(authorities = "PERM_TICKET_EMAIL_VIEW")
    void getContent_returns404_whenEmailIdMismatch() throws Exception {
        Ticket ticket = makeTicket(1L, PUBLIC_ID);
        // Attachment belongs to different email
        AttachmentMetadata meta = makeMetadata(30L, 1L, "other-email-id", "file.pdf", "application/pdf");

        when(ticketAuth.canViewTicketEmailByPublicId(any(), any())).thenReturn(true);
        when(ticketQueryService.getByPublicId(PUBLIC_ID)).thenReturn(ticket);
        when(attachmentService.getById(30L)).thenReturn(meta);

        mockMvc.perform(get("/api/tickets/{publicId}/emails/{emailId}/attachments/{id}/content",
                        PUBLIC_ID, EMAIL_ID, 30L))
                .andExpect(status().isNotFound());
    }

    @Test
    void getContent_returns401_whenUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/tickets/{publicId}/emails/{emailId}/attachments/{id}/content",
                        PUBLIC_ID, EMAIL_ID, 10L))
                .andExpect(status().isUnauthorized());
    }

    // ── Helpers ───────────────────────────────���───────────────────────��───────

    private Ticket makeTicket(Long id, UUID publicId) {
        Ticket t = new Ticket();
        try {
            var f = Ticket.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(t, id);
            var pf = Ticket.class.getDeclaredField("publicId");
            pf.setAccessible(true);
            pf.set(t, publicId);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return t;
    }

    private AttachmentMetadata makeMetadata(Long id, Long ticketId, String emailId,
                                            String fileName, String contentType) {
        AttachmentMetadata m = new AttachmentMetadata();
        try {
            var f = AttachmentMetadata.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(m, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        m.setTicketId(ticketId);
        m.setEmailId(emailId);
        m.setFileName(fileName);
        m.setObjectKey("tickets/some/key/" + fileName);
        m.setContentType(contentType);
        m.setSize(1024L);
        return m;
    }

    private AttachmentMetadataResponse makeResponse(Long id, String fileName,
                                                     String contentType, boolean previewSupported) {
        return new AttachmentMetadataResponse(
                id, 1L, PUBLIC_ID, EMAIL_ID, fileName, contentType, 1024L,
                "EMAIL_INBOUND",
                "/api/tickets/" + PUBLIC_ID + "/emails/" + EMAIL_ID + "/attachments/" + id + "/content",
                previewSupported,
                Instant.now());
    }
}
