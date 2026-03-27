package com.caseflow.identity.api;

import com.caseflow.identity.api.dto.CreateGroupRequest;
import com.caseflow.identity.api.dto.GroupResponse;
import com.caseflow.identity.api.dto.GroupSummaryResponse;
import com.caseflow.identity.api.dto.UpdateGroupRequest;
import com.caseflow.identity.api.mapper.GroupMapper;
import com.caseflow.identity.domain.GroupType;
import com.caseflow.identity.service.GroupService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Groups", description = "User group management")
@RestController
@RequestMapping("/api/groups")
public class GroupController {

    private final GroupService groupService;
    private final GroupMapper groupMapper;

    public GroupController(GroupService groupService, GroupMapper groupMapper) {
        this.groupService = groupService;
        this.groupMapper = groupMapper;
    }

    @PostMapping
    public ResponseEntity<GroupResponse> createGroup(@Valid @RequestBody CreateGroupRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                groupMapper.toResponse(groupService.createGroup(request.name(), request.type()))
        );
    }

    @GetMapping("/{id}")
    public ResponseEntity<GroupResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(groupMapper.toResponse(groupService.getById(id)));
    }

    /**
     * Returns all active groups by default. For all groups of a specific type,
     * use /by-type?type=... instead.
     */
    @GetMapping
    public ResponseEntity<List<GroupSummaryResponse>> listGroups() {
        return ResponseEntity.ok(
                groupService.findActiveGroups().stream().map(groupMapper::toSummaryResponse).toList()
        );
    }

    @GetMapping("/by-type")
    public ResponseEntity<List<GroupSummaryResponse>> getByType(@RequestParam GroupType type) {
        return ResponseEntity.ok(
                groupService.findByType(type).stream().map(groupMapper::toSummaryResponse).toList()
        );
    }

    @PutMapping("/{id}")
    public ResponseEntity<GroupResponse> updateGroup(@PathVariable Long id,
                                                     @Valid @RequestBody UpdateGroupRequest request) {
        return ResponseEntity.ok(
                groupMapper.toResponse(groupService.updateGroup(id, request.name(), request.type()))
        );
    }

    @PatchMapping("/{id}/activate")
    public ResponseEntity<Void> activate(@PathVariable Long id) {
        groupService.activate(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/deactivate")
    public ResponseEntity<Void> deactivate(@PathVariable Long id) {
        groupService.deactivate(id);
        return ResponseEntity.noContent().build();
    }
}
