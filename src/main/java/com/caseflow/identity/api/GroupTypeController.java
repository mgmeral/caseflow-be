package com.caseflow.identity.api;

import com.caseflow.identity.api.dto.CreateGroupTypeRequest;
import com.caseflow.identity.api.dto.GroupTypeResponse;
import com.caseflow.identity.api.dto.GroupTypeSummaryResponse;
import com.caseflow.identity.api.dto.UpdateGroupTypeRequest;
import com.caseflow.identity.api.mapper.GroupTypeMapper;
import com.caseflow.identity.service.GroupTypeService;
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

@Tag(name = "Group Types", description = "Group type reference data management")
@RestController
@RequestMapping("/api/group-types")
public class GroupTypeController {

    private static final Logger log = LoggerFactory.getLogger(GroupTypeController.class);

    private final GroupTypeService groupTypeService;
    private final GroupTypeMapper groupTypeMapper;

    public GroupTypeController(GroupTypeService groupTypeService, GroupTypeMapper groupTypeMapper) {
        this.groupTypeService = groupTypeService;
        this.groupTypeMapper = groupTypeMapper;
    }

    @PostMapping
    public ResponseEntity<GroupTypeResponse> createGroupType(@Valid @RequestBody CreateGroupTypeRequest request) {
        log.info("POST /group-types — code: '{}', name: '{}'", request.code(), request.name());
        GroupTypeResponse response = groupTypeMapper.toResponse(groupTypeService.createGroupType(request));
        log.info("POST /group-types succeeded — id: {}", response.id());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<GroupTypeResponse> getById(@PathVariable Long id) {
        log.info("GET /group-types/{}", id);
        return ResponseEntity.ok(groupTypeMapper.toResponse(groupTypeService.getById(id)));
    }

    @GetMapping
    public ResponseEntity<List<GroupTypeSummaryResponse>> listGroupTypes() {
        return ResponseEntity.ok(
                groupTypeService.findActiveGroupTypes().stream()
                        .map(groupTypeMapper::toSummaryResponse)
                        .toList()
        );
    }

    @PutMapping("/{id}")
    public ResponseEntity<GroupTypeResponse> updateGroupType(@PathVariable Long id,
                                                             @Valid @RequestBody UpdateGroupTypeRequest request) {
        log.info("PUT /group-types/{} — code: '{}', name: '{}'", id, request.code(), request.name());
        GroupTypeResponse response = groupTypeMapper.toResponse(groupTypeService.updateGroupType(id, request));
        log.info("PUT /group-types/{} succeeded", id);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}/activate")
    public ResponseEntity<Void> activate(@PathVariable Long id) {
        log.info("PATCH /group-types/{}/activate", id);
        groupTypeService.activate(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/deactivate")
    public ResponseEntity<Void> deactivate(@PathVariable Long id) {
        log.info("PATCH /group-types/{}/deactivate", id);
        groupTypeService.deactivate(id);
        return ResponseEntity.noContent().build();
    }
}
