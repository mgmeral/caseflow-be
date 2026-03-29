package com.caseflow.customer.api;

import com.caseflow.auth.CaseFlowUserDetailsService;
import com.caseflow.auth.JwtTokenService;
import com.caseflow.common.exception.ContactNotFoundException;
import com.caseflow.common.security.SecurityConfig;
import com.caseflow.customer.api.dto.ContactResponse;
import com.caseflow.customer.api.dto.ContactSummaryResponse;
import com.caseflow.customer.api.dto.CreateContactRequest;
import com.caseflow.customer.api.dto.UpdateContactRequest;
import com.caseflow.customer.api.mapper.ContactMapper;
import com.caseflow.customer.domain.Contact;
import com.caseflow.customer.service.ContactService;
import com.caseflow.ticket.security.TicketAuthorizationService;
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
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ContactController.class)
@Import(SecurityConfig.class)
class ContactControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private JwtTokenService jwtTokenService;
    @MockBean private CaseFlowUserDetailsService userDetailsService;
    @MockBean private ContactService contactService;
    @MockBean private ContactMapper contactMapper;
    @MockBean(name = "ticketAuth") private TicketAuthorizationService ticketAuth;

    // ── POST /api/contacts ────────────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "AGENT")
    void create_returns201_withContactResponse() throws Exception {
        CreateContactRequest request = new CreateContactRequest(1L, "alice@example.com", "Alice", false);
        ContactResponse response = makeResponse(10L, 1L, "alice@example.com", "Alice");

        when(contactService.createContact(eq(1L), eq("alice@example.com"), eq("Alice"), eq(false)))
                .thenReturn(new Contact());
        when(contactMapper.toResponse(any())).thenReturn(response);

        mockMvc.perform(post("/api/contacts")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.email").value("alice@example.com"))
                .andExpect(jsonPath("$.customerId").value(1));
    }

    @Test
    @WithMockUser(roles = "AGENT")
    void create_returns400_whenEmailIsBlank() throws Exception {
        CreateContactRequest request = new CreateContactRequest(1L, "", "Alice", false);

        mockMvc.perform(post("/api/contacts")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void create_returns401_whenUnauthenticated() throws Exception {
        mockMvc.perform(post("/api/contacts").with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    // ── GET /api/contacts/{id} ────────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "VIEWER")
    void getById_returns200_whenFound() throws Exception {
        ContactResponse response = makeResponse(10L, 1L, "alice@example.com", "Alice");

        when(contactService.getById(10L)).thenReturn(new Contact());
        when(contactMapper.toResponse(any())).thenReturn(response);

        mockMvc.perform(get("/api/contacts/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.name").value("Alice"));
    }

    @Test
    @WithMockUser(roles = "VIEWER")
    void getById_returns404_whenNotFound() throws Exception {
        when(contactService.getById(99L)).thenThrow(new ContactNotFoundException(99L));

        mockMvc.perform(get("/api/contacts/99"))
                .andExpect(status().isNotFound());
    }

    // ── GET /api/contacts ─────────────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "VIEWER")
    void list_returns200_withSummaryList() throws Exception {
        ContactSummaryResponse summary = new ContactSummaryResponse(10L, 1L, "alice@example.com", "Alice", false);

        when(contactService.findAll()).thenReturn(List.of(new Contact()));
        when(contactMapper.toSummaryResponse(any())).thenReturn(summary);

        mockMvc.perform(get("/api/contacts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(10))
                .andExpect(jsonPath("$[0].email").value("alice@example.com"));
    }

    // ── GET /api/contacts/by-email ────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "VIEWER")
    void getByEmail_returns200_whenFound() throws Exception {
        ContactResponse response = makeResponse(10L, 1L, "alice@example.com", "Alice");

        when(contactService.findByEmail("alice@example.com")).thenReturn(Optional.of(new Contact()));
        when(contactMapper.toResponse(any())).thenReturn(response);

        mockMvc.perform(get("/api/contacts/by-email").param("email", "alice@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("alice@example.com"));
    }

    @Test
    @WithMockUser(roles = "VIEWER")
    void getByEmail_returns404_whenNotFound() throws Exception {
        when(contactService.findByEmail(anyString())).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/contacts/by-email").param("email", "missing@example.com"))
                .andExpect(status().isNotFound());
    }

    // ── GET /api/contacts/by-customer/{customerId} ────────────────────────────

    @Test
    @WithMockUser(roles = "VIEWER")
    void getByCustomer_returns200_withList() throws Exception {
        ContactSummaryResponse summary = new ContactSummaryResponse(10L, 1L, "alice@example.com", "Alice", false);

        when(contactService.findByCustomerId(1L)).thenReturn(List.of(new Contact()));
        when(contactMapper.toSummaryResponse(any())).thenReturn(summary);

        mockMvc.perform(get("/api/contacts/by-customer/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].customerId").value(1));
    }

    // ── PUT /api/contacts/{id} ────────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "AGENT")
    void update_returns200() throws Exception {
        UpdateContactRequest request = new UpdateContactRequest("Alice Updated", true, true);
        ContactResponse response = makeResponse(10L, 1L, "alice@example.com", "Alice Updated");

        when(contactService.updateContact(eq(10L), anyString(), eq(true), eq(true)))
                .thenReturn(new Contact());
        when(contactMapper.toResponse(any())).thenReturn(response);

        mockMvc.perform(put("/api/contacts/10")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private ContactResponse makeResponse(Long id, Long customerId, String email, String name) {
        return new ContactResponse(id, customerId, email, name, false, true, Instant.now());
    }
}
