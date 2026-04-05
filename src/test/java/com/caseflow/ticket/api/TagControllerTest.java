package com.caseflow.ticket.api;

import com.caseflow.auth.CaseFlowUserDetails;
import com.caseflow.auth.CaseFlowUserDetailsService;
import com.caseflow.auth.JwtTokenService;
import com.caseflow.common.security.SecurityConfig;
import com.caseflow.ticket.api.dto.TagRequest;
import com.caseflow.ticket.api.dto.TagResponse;
import com.caseflow.ticket.security.TicketAuthorizationService;
import com.caseflow.ticket.service.TagService;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TagController.class)
@Import(SecurityConfig.class)
class TagControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private TagService tagService;
    @MockBean private JwtTokenService jwtTokenService;
    @MockBean private CaseFlowUserDetailsService userDetailsService;
    @MockBean(name = "ticketAuth") private TicketAuthorizationService ticketAuth;

    @Test
    @WithMockUser(authorities = "PERM_TICKET_READ")
    void listActiveTags_returns200() throws Exception {
        TagResponse tag = new TagResponse(1L, "BUG", "Bug", null, "#FF0000", true,
                Instant.now(), Instant.now());
        when(tagService.listActive()).thenReturn(List.of(tag));

        mockMvc.perform(get("/api/tags"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("BUG"))
                .andExpect(jsonPath("$[0].isActive").value(true));
    }

    @Test
    @WithMockUser
    void listActiveTags_returns403_withoutPermission() throws Exception {
        mockMvc.perform(get("/api/tags"))
                .andExpect(status().isForbidden());
    }

    @Test
    void createTag_returns201_withAdminPermission() throws Exception {
        when(ticketAuth.canManageTags(any())).thenReturn(true);
        TagResponse created = new TagResponse(1L, "FEATURE", "Feature Request", null, null, true,
                Instant.now(), Instant.now());
        when(tagService.create(any(), anyLong())).thenReturn(created);

        CaseFlowUserDetails principal = buildPrincipal();
        TagRequest request = new TagRequest("FEATURE", "Feature Request", null, null, true);

        mockMvc.perform(post("/api/tags")
                        .with(user(principal))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("FEATURE"));
    }

    @Test
    @WithMockUser
    void createTag_returns403_withoutAdminPermission() throws Exception {
        when(ticketAuth.canManageTags(any())).thenReturn(false);

        TagRequest request = new TagRequest("BUG", "Bug", null, null, true);
        mockMvc.perform(post("/api/tags")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    private CaseFlowUserDetails buildPrincipal() {
        CaseFlowUserDetails p = mock(CaseFlowUserDetails.class);
        when(p.getUserId()).thenReturn(1L);
        when(p.getUsername()).thenReturn("admin");
        when(p.getPassword()).thenReturn("");
        when(p.isEnabled()).thenReturn(true);
        when(p.isAccountNonExpired()).thenReturn(true);
        when(p.isAccountNonLocked()).thenReturn(true);
        when(p.isCredentialsNonExpired()).thenReturn(true);
        when(p.getAuthorities()).thenReturn(List.of());
        return p;
    }
}
