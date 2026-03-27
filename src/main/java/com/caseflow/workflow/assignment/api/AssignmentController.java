package com.caseflow.workflow.assignment.api;

import com.caseflow.common.security.SecurityContextHelper;
import com.caseflow.workflow.assignment.AssignmentService;
import com.caseflow.workflow.assignment.dto.AssignTicketRequest;
import com.caseflow.workflow.assignment.dto.AssignmentResponse;
import com.caseflow.workflow.assignment.dto.ReassignTicketRequest;
import com.caseflow.workflow.assignment.dto.UnassignTicketRequest;
import com.caseflow.workflow.assignment.mapper.AssignmentMapper;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Assignments", description = "Ticket assignment workflow")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/assignments")
public class AssignmentController {

    private final AssignmentService assignmentService;
    private final AssignmentMapper assignmentMapper;

    public AssignmentController(AssignmentService assignmentService, AssignmentMapper assignmentMapper) {
        this.assignmentService = assignmentService;
        this.assignmentMapper = assignmentMapper;
    }

    @PostMapping("/assign")
    public ResponseEntity<AssignmentResponse> assign(@Valid @RequestBody AssignTicketRequest request) {
        Long userId = SecurityContextHelper.requireCurrentUserId();
        return ResponseEntity.ok(
                assignmentMapper.toResponse(
                        assignmentService.assign(
                                request.ticketId(), request.assignedUserId(),
                                request.assignedGroupId(), userId))
        );
    }

    @PostMapping("/reassign")
    public ResponseEntity<AssignmentResponse> reassign(@Valid @RequestBody ReassignTicketRequest request) {
        Long userId = SecurityContextHelper.requireCurrentUserId();
        return ResponseEntity.ok(
                assignmentMapper.toResponse(
                        assignmentService.reassign(
                                request.ticketId(), request.newUserId(),
                                request.newGroupId(), userId))
        );
    }

    @PostMapping("/unassign")
    public ResponseEntity<Void> unassign(@Valid @RequestBody UnassignTicketRequest request) {
        Long userId = SecurityContextHelper.requireCurrentUserId();
        assignmentService.unassign(request.ticketId(), userId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/by-ticket/{ticketId}")
    public ResponseEntity<AssignmentResponse> getActiveAssignment(@PathVariable Long ticketId) {
        return assignmentService.getActiveAssignment(ticketId)
                .map(a -> ResponseEntity.ok(assignmentMapper.toResponse(a)))
                .orElse(ResponseEntity.notFound().build());
    }
}
