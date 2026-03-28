package com.caseflow.identity.api;

import com.caseflow.auth.JwtTokenService;
import com.caseflow.common.exception.GroupNotFoundException;
import com.caseflow.common.security.SecurityConfig;
import com.caseflow.identity.api.dto.CreateGroupRequest;
import com.caseflow.identity.api.dto.GroupResponse;
import com.caseflow.identity.api.dto.GroupSummaryResponse;
import com.caseflow.identity.api.dto.UpdateGroupRequest;
import com.caseflow.identity.api.mapper.GroupMapper;
import com.caseflow.identity.domain.Group;
import com.caseflow.identity.service.GroupService;
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

@WebMvcTest(GroupController.class)
@Import(SecurityConfig.class)
class GroupControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private JwtTokenService jwtTokenService;

    @MockBean
    private GroupService groupService;

    @MockBean
    private GroupMapper groupMapper;

    // ── POST /api/groups ──────────────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void createGroup_returns201_withMembers() throws Exception {
        CreateGroupRequest request = new CreateGroupRequest(
                "Support", 1L, "Handles support tickets", List.of(1L, 2L));
        Group group = new Group();
        GroupResponse response = makeGroupResponse(1L, "Support", 1L, "SUPPORT", "Support", "Handles support tickets", 2);

        when(groupService.createGroup(any(CreateGroupRequest.class))).thenReturn(group);
        when(groupMapper.toResponse(group)).thenReturn(response);

        mockMvc.perform(post("/api/groups")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Support"))
                .andExpect(jsonPath("$.groupTypeCode").value("SUPPORT"))
                .andExpect(jsonPath("$.description").value("Handles support tickets"))
                .andExpect(jsonPath("$.memberCount").value(2));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createGroup_returns201_withNoMembers() throws Exception {
        CreateGroupRequest request = new CreateGroupRequest("Ops", 2L, null, null);
        Group group = new Group();
        GroupResponse response = makeGroupResponse(2L, "Ops", 2L, "OPERATIONS", "Operations", null, 0);

        when(groupService.createGroup(any(CreateGroupRequest.class))).thenReturn(group);
        when(groupMapper.toResponse(group)).thenReturn(response);

        mockMvc.perform(post("/api/groups")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.memberCount").value(0));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createGroup_returns400_whenNameIsBlank() throws Exception {
        CreateGroupRequest request = new CreateGroupRequest("", 1L, null, null);

        mockMvc.perform(post("/api/groups")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createGroup_returns400_whenGroupTypeIdIsNull() throws Exception {
        CreateGroupRequest request = new CreateGroupRequest("Support", null, null, null);

        mockMvc.perform(post("/api/groups")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    // ── GET /api/groups/{id} ──────────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "AGENT")
    void getById_returns200_withMembersAndCount() throws Exception {
        Group group = new Group();
        GroupResponse response = makeGroupResponse(2L, "Ops", 2L, "OPERATIONS", "Operations", null, 3);

        when(groupService.getById(2L)).thenReturn(group);
        when(groupMapper.toResponse(group)).thenReturn(response);

        mockMvc.perform(get("/api/groups/2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(2))
                .andExpect(jsonPath("$.groupTypeCode").value("OPERATIONS"))
                .andExpect(jsonPath("$.memberCount").value(3))
                .andExpect(jsonPath("$.members").isArray());
    }

    @Test
    @WithMockUser(roles = "AGENT")
    void getById_returns404_whenGroupNotFound() throws Exception {
        when(groupService.getById(99L)).thenThrow(new GroupNotFoundException(99L));

        mockMvc.perform(get("/api/groups/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").exists());
    }

    // ── GET /api/groups ───────────────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "AGENT")
    void listGroups_returns200_withSummaryList() throws Exception {
        Group group = new Group();
        GroupSummaryResponse summary = new GroupSummaryResponse(
                1L, "Support", 1L, "SUPPORT", "Support", true, 2, List.of(10L, 20L));

        when(groupService.findActiveGroups()).thenReturn(List.of(group));
        when(groupMapper.toSummaryResponse(group)).thenReturn(summary);

        mockMvc.perform(get("/api/groups"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Support"))
                .andExpect(jsonPath("$[0].groupTypeCode").value("SUPPORT"))
                .andExpect(jsonPath("$[0].memberCount").value(2))
                .andExpect(jsonPath("$[0].memberIds").isArray())
                .andExpect(jsonPath("$[0].memberIds[0]").value(10));
    }

    // ── PUT /api/groups/{id} ──────────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateGroup_returns200_replacingMembers() throws Exception {
        UpdateGroupRequest request = new UpdateGroupRequest(
                "Support v2", 1L, "Updated", List.of(3L, 4L));
        Group group = new Group();
        GroupResponse response = makeGroupResponse(1L, "Support v2", 1L, "SUPPORT", "Support", "Updated", 2);

        when(groupService.updateGroup(eq(1L), any(UpdateGroupRequest.class))).thenReturn(group);
        when(groupMapper.toResponse(group)).thenReturn(response);

        mockMvc.perform(put("/api/groups/1")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Support v2"))
                .andExpect(jsonPath("$.description").value("Updated"))
                .andExpect(jsonPath("$.memberCount").value(2));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateGroup_returns200_leavingMembersUnchangedWhenUserIdsIsNull() throws Exception {
        // userIds: null means no membership change
        UpdateGroupRequest request = new UpdateGroupRequest(
                "Support v2", 1L, "Updated", null);
        Group group = new Group();
        GroupResponse response = makeGroupResponse(1L, "Support v2", 1L, "SUPPORT", "Support", "Updated", 5);

        when(groupService.updateGroup(eq(1L), any(UpdateGroupRequest.class))).thenReturn(group);
        when(groupMapper.toResponse(group)).thenReturn(response);

        mockMvc.perform(put("/api/groups/1")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memberCount").value(5));
    }

    // ── PATCH activate/deactivate ─────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void activate_returns204() throws Exception {
        mockMvc.perform(patch("/api/groups/3/activate").with(csrf()))
                .andExpect(status().isNoContent());
        verify(groupService).activate(3L);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void deactivate_returns204() throws Exception {
        mockMvc.perform(patch("/api/groups/3/deactivate").with(csrf()))
                .andExpect(status().isNoContent());
        verify(groupService).deactivate(3L);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private GroupResponse makeGroupResponse(Long id, String name, Long groupTypeId,
                                            String groupTypeCode, String groupTypeName,
                                            String description, int memberCount) {
        return new GroupResponse(id, name, groupTypeId, groupTypeCode, groupTypeName,
                description, true, Instant.now(), memberCount, List.of());
    }
}
