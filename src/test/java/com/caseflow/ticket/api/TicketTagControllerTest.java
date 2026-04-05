package com.caseflow.ticket.api;

import com.caseflow.auth.CaseFlowUserDetails;
import com.caseflow.auth.CaseFlowUserDetailsService;
import com.caseflow.auth.JwtTokenService;
import com.caseflow.common.security.SecurityConfig;
import com.caseflow.ticket.api.dto.TicketTagResponse;
import com.caseflow.ticket.security.TicketAuthorizationService;
import com.caseflow.ticket.service.TagService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TicketTagController.class)
@Import(SecurityConfig.class)
class TicketTagControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private TagService tagService;
    @MockBean private JwtTokenService jwtTokenService;
    @MockBean private CaseFlowUserDetailsService userDetailsService;
    @MockBean(name = "ticketAuth") private TicketAuthorizationService ticketAuth;

    @Test
    @WithMockUser
    void listTags_returns200_withTicketTags() throws Exception {
        when(ticketAuth.canReadTicket(any(), anyLong())).thenReturn(true);
        TicketTagResponse tag = new TicketTagResponse(1L, "BUG", "Bug", "#FF0000", Instant.now(), 99L);
        when(tagService.listTicketTags(10L)).thenReturn(List.of(tag));

        mockMvc.perform(get("/api/tickets/10/tags"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].tagCode").value("BUG"))
                .andExpect(jsonPath("$[0].tagId").value(1));
    }

    @Test
    @WithMockUser
    void listTags_returns403_withoutReadPermission() throws Exception {
        when(ticketAuth.canReadTicket(any(), anyLong())).thenReturn(false);

        mockMvc.perform(get("/api/tickets/10/tags"))
                .andExpect(status().isForbidden());
    }

    @Test
    void addTag_returns201_withTagResponse() throws Exception {
        CaseFlowUserDetails principal = mockPrincipal(99L);
        when(ticketAuth.canTagTicket(any(), anyLong())).thenReturn(true);
        TicketTagResponse tag = new TicketTagResponse(5L, "VIP", "VIP Customer", null, Instant.now(), 99L);
        when(tagService.addTagToTicket(anyLong(), anyLong(), anyLong())).thenReturn(tag);

        mockMvc.perform(post("/api/tickets/10/tags/5")
                        .with(user(principal))
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tagCode").value("VIP"));
    }

    @Test
    @WithMockUser
    void addTag_returns403_withoutPermission() throws Exception {
        when(ticketAuth.canTagTicket(any(), anyLong())).thenReturn(false);

        mockMvc.perform(post("/api/tickets/10/tags/5").with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void removeTag_returns204() throws Exception {
        CaseFlowUserDetails principal = mockPrincipal(99L);
        when(ticketAuth.canTagTicket(any(), anyLong())).thenReturn(true);

        mockMvc.perform(delete("/api/tickets/10/tags/5")
                        .with(user(principal))
                        .with(csrf()))
                .andExpect(status().isNoContent());
    }

    private CaseFlowUserDetails mockPrincipal(Long userId) {
        CaseFlowUserDetails p = mock(CaseFlowUserDetails.class);
        when(p.getUserId()).thenReturn(userId);
        when(p.getUsername()).thenReturn("agent");
        when(p.getPassword()).thenReturn("");
        when(p.isEnabled()).thenReturn(true);
        when(p.isAccountNonExpired()).thenReturn(true);
        when(p.isAccountNonLocked()).thenReturn(true);
        when(p.isCredentialsNonExpired()).thenReturn(true);
        when(p.getAuthorities()).thenReturn(List.of());
        return p;
    }
}
