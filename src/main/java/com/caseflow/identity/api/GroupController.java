package com.caseflow.identity.api;

import com.caseflow.identity.api.dto.CreateGroupRequest;
import com.caseflow.identity.api.dto.GroupResponse;
import com.caseflow.identity.api.dto.GroupSummaryResponse;
import com.caseflow.identity.api.dto.UpdateGroupRequest;
import com.caseflow.identity.api.mapper.GroupMapper;
import com.caseflow.identity.service.GroupService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Groups", description = "User group management")
@RestController
@RequestMapping("/api/groups")
public class GroupController {

    private static final Logger log = LoggerFactory.getLogger(GroupController.class);

    private final GroupService groupService;
    private final GroupMapper groupMapper;

    public GroupController(GroupService groupService, GroupMapper groupMapper) {
        this.groupService = groupService;
        this.groupMapper = groupMapper;
    }

    @PostMapping
    public ResponseEntity<GroupResponse> createGroup(@Valid @RequestBody CreateGroupRequest request) {
        log.info("POST /groups — name: '{}', groupTypeId: {}", request.name(), request.groupTypeId());
        GroupResponse response = groupMapper.toResponse(groupService.createGroup(request));
        log.info("POST /groups succeeded — groupId: {}", response.id());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<GroupResponse> getById(@PathVariable Long id) {
        log.info("GET /groups/{}", id);
        return ResponseEntity.ok(groupMapper.toResponse(groupService.getById(id)));
    }

    /**
     * Returns all active groups.
     */
    @GetMapping
    public ResponseEntity<List<GroupSummaryResponse>> listGroups() {
        return ResponseEntity.ok(
                groupService.findActiveGroups().stream().map(groupMapper::toSummaryResponse).toList()
        );
    }

    @PutMapping("/{id}")
    public ResponseEntity<GroupResponse> updateGroup(@PathVariable Long id,
                                                     @Valid @RequestBody UpdateGroupRequest request) {
        log.info("PUT /groups/{} — name: '{}', groupTypeId: {}", id, request.name(), request.groupTypeId());
        GroupResponse response = groupMapper.toResponse(groupService.updateGroup(id, request));
        log.info("PUT /groups/{} succeeded", id);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}/activate")
    public ResponseEntity<Void> activate(@PathVariable Long id) {
        log.info("PATCH /groups/{}/activate", id);
        groupService.activate(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/deactivate")
    public ResponseEntity<Void> deactivate(@PathVariable Long id) {
        log.info("PATCH /groups/{}/deactivate", id);
        groupService.deactivate(id);
        return ResponseEntity.noContent().build();
    }
}
