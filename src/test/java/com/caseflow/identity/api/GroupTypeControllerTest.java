package com.caseflow.identity.api;

import com.caseflow.auth.CaseFlowUserDetailsService;
import com.caseflow.auth.JwtTokenService;
import com.caseflow.common.exception.GroupTypeNotFoundException;
import com.caseflow.common.security.SecurityConfig;
import com.caseflow.identity.api.dto.CreateGroupTypeRequest;
import com.caseflow.identity.api.dto.GroupTypeResponse;
import com.caseflow.identity.api.dto.GroupTypeSummaryResponse;
import com.caseflow.identity.api.dto.UpdateGroupTypeRequest;
import com.caseflow.identity.api.mapper.GroupTypeMapper;
import com.caseflow.identity.domain.GroupType;
import com.caseflow.identity.service.GroupTypeService;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(GroupTypeController.class)
@Import(SecurityConfig.class)
class GroupTypeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private JwtTokenService jwtTokenService;

    @MockBean
    private CaseFlowUserDetailsService userDetailsService;

    @MockBean
    private GroupTypeService groupTypeService;

    @MockBean
    private GroupTypeMapper groupTypeMapper;

    // ── POST /api/group-types ─────────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void createGroupType_returns201() throws Exception {
        CreateGroupTypeRequest request = new CreateGroupTypeRequest("SUPPORT", "Support", "Handles support tickets");
        GroupType groupType = new GroupType();
        GroupTypeResponse response = makeGroupTypeResponse(1L, "SUPPORT", "Support", "Handles support tickets");

        when(groupTypeService.createGroupType(any(CreateGroupTypeRequest.class))).thenReturn(groupType);
        when(groupTypeMapper.toResponse(groupType)).thenReturn(response);

        mockMvc.perform(post("/api/group-types")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.code").value("SUPPORT"))
                .andExpect(jsonPath("$.name").value("Support"))
                .andExpect(jsonPath("$.description").value("Handles support tickets"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createGroupType_returns400_whenCodeIsBlank() throws Exception {
        CreateGroupTypeRequest request = new CreateGroupTypeRequest("", "Support", null);

        mockMvc.perform(post("/api/group-types")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createGroupType_returns400_whenNameIsBlank() throws Exception {
        CreateGroupTypeRequest request = new CreateGroupTypeRequest("SUPPORT", "", null);

        mockMvc.perform(post("/api/group-types")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    // ── GET /api/group-types/{id} ─────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "AGENT")
    void getById_returns200() throws Exception {
        GroupType groupType = new GroupType();
        GroupTypeResponse response = makeGroupTypeResponse(1L, "SUPPORT", "Support", null);

        when(groupTypeService.getById(1L)).thenReturn(groupType);
        when(groupTypeMapper.toResponse(groupType)).thenReturn(response);

        mockMvc.perform(get("/api/group-types/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.code").value("SUPPORT"));
    }

    @Test
    @WithMockUser(roles = "AGENT")
    void getById_returns404_whenNotFound() throws Exception {
        when(groupTypeService.getById(99L)).thenThrow(new GroupTypeNotFoundException(99L));

        mockMvc.perform(get("/api/group-types/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").exists());
    }

    // ── GET /api/group-types ──────────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "AGENT")
    void listGroupTypes_returns200_withSummaryList() throws Exception {
        GroupType groupType = new GroupType();
        GroupTypeSummaryResponse summary = new GroupTypeSummaryResponse(1L, "SUPPORT", "Support", true);

        when(groupTypeService.findActiveGroupTypes()).thenReturn(List.of(groupType));
        when(groupTypeMapper.toSummaryResponse(groupType)).thenReturn(summary);

        mockMvc.perform(get("/api/group-types"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].code").value("SUPPORT"))
                .andExpect(jsonPath("$[0].name").value("Support"))
                .andExpect(jsonPath("$[0].isActive").value(true));
    }

    // ── PUT /api/group-types/{id} ─────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateGroupType_returns200() throws Exception {
        UpdateGroupTypeRequest request = new UpdateGroupTypeRequest("SUPPORT_V2", "Support v2", "Updated");
        GroupType groupType = new GroupType();
        GroupTypeResponse response = makeGroupTypeResponse(1L, "SUPPORT_V2", "Support v2", "Updated");

        when(groupTypeService.updateGroupType(eq(1L), any(UpdateGroupTypeRequest.class))).thenReturn(groupType);
        when(groupTypeMapper.toResponse(groupType)).thenReturn(response);

        mockMvc.perform(put("/api/group-types/1")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUPPORT_V2"))
                .andExpect(jsonPath("$.name").value("Support v2"))
                .andExpect(jsonPath("$.description").value("Updated"));
    }

    // ── PATCH activate/deactivate ─────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void activate_returns204() throws Exception {
        mockMvc.perform(patch("/api/group-types/3/activate").with(csrf()))
                .andExpect(status().isNoContent());
        verify(groupTypeService).activate(3L);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void deactivate_returns204() throws Exception {
        mockMvc.perform(patch("/api/group-types/3/deactivate").with(csrf()))
                .andExpect(status().isNoContent());
        verify(groupTypeService).deactivate(3L);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private GroupTypeResponse makeGroupTypeResponse(Long id, String code, String name, String description) {
        return new GroupTypeResponse(id, code, name, description, true, Instant.now());
    }
}
