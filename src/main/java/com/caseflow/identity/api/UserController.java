package com.caseflow.identity.api;

import com.caseflow.auth.CaseFlowUserDetails;
import com.caseflow.identity.api.dto.ChangePasswordRequest;
import com.caseflow.identity.api.dto.CreateUserRequest;
import com.caseflow.identity.api.dto.UpdateProfileRequest;
import com.caseflow.identity.api.dto.UpdateUserRequest;
import com.caseflow.identity.api.dto.UserProfileResponse;
import com.caseflow.identity.api.dto.UserResponse;
import com.caseflow.identity.api.dto.UserSummaryResponse;
import com.caseflow.identity.api.mapper.UserMapper;
import com.caseflow.identity.domain.User;
import com.caseflow.identity.service.UserService;
import com.caseflow.common.api.PagedResponse;
import com.caseflow.ticket.repository.TicketRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Tag(name = "Users", description = "User account management")
@RestController
@RequestMapping("/api/users")
public class UserController {

    private static final Logger log = LoggerFactory.getLogger(UserController.class);

    private final UserService userService;
    private final UserMapper userMapper;
    private final TicketRepository ticketRepository;

    public UserController(UserService userService, UserMapper userMapper, TicketRepository ticketRepository) {
        this.userService = userService;
        this.userMapper = userMapper;
        this.ticketRepository = ticketRepository;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PERM_USER_MANAGE')")
    public ResponseEntity<UserResponse> createUser(@Valid @RequestBody CreateUserRequest request) {
        log.info("POST /users — username: '{}'", request.username());
        UserResponse response = userMapper.toResponse(userService.createUser(request));
        log.info("POST /users succeeded — userId: {}, username: '{}'", response.id(), request.username());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('PERM_USER_MANAGE', 'PERM_USER_READ')")
    public ResponseEntity<UserResponse> getById(@PathVariable Long id) {
        log.info("GET /users/{}", id);
        return ResponseEntity.ok(userMapper.toResponse(userService.getById(id)));
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('PERM_USER_MANAGE', 'PERM_USER_READ')")
    public ResponseEntity<PagedResponse<UserSummaryResponse>> listUsers(
            @PageableDefault(size = 20, sort = "username", direction = Sort.Direction.ASC) Pageable pageable) {
        Page<User> page = userService.findAll(pageable);

        // Bulk-computed once per page rather than per row — see countActiveByAssignedUser javadoc.
        Map<Long, Long> openCountsByUserId = new HashMap<>();
        for (Object[] row : ticketRepository.countActiveByAssignedUser()) {
            Long userId = (Long) row[0];
            long count = ((Number) row[2]).longValue();
            openCountsByUserId.merge(userId, count, Long::sum);
        }

        return ResponseEntity.ok(PagedResponse.from(page.map(user -> {
            UserSummaryResponse base = userMapper.toSummaryResponse(user);
            long openTicketCount = openCountsByUserId.getOrDefault(user.getId(), 0L);
            return new UserSummaryResponse(base.id(), base.username(), base.fullName(),
                    base.roleId(), base.roleCode(), base.isActive(), openTicketCount);
        })));
    }

    @GetMapping("/by-username")
    @PreAuthorize("hasAnyAuthority('PERM_USER_MANAGE', 'PERM_USER_READ')")
    public ResponseEntity<UserResponse> getByUsername(@RequestParam String username) {
        log.info("GET /users/by-username — username: '{}'", username);
        return userService.findByUsername(username)
                .map(user -> ResponseEntity.ok(userMapper.toResponse(user)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/by-email")
    @PreAuthorize("hasAnyAuthority('PERM_USER_MANAGE', 'PERM_USER_READ')")
    public ResponseEntity<UserResponse> getByEmail(@RequestParam String email) {
        return userService.findByEmail(email)
                .map(user -> ResponseEntity.ok(userMapper.toResponse(user)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_USER_MANAGE')")
    public ResponseEntity<UserResponse> updateUser(@PathVariable Long id,
                                                   @Valid @RequestBody UpdateUserRequest request) {
        log.info("PUT /users/{}", id);
        UserResponse response = userMapper.toResponse(userService.updateUser(id, request));
        log.info("PUT /users/{} succeeded", id);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}/activate")
    @PreAuthorize("hasAuthority('PERM_USER_MANAGE')")
    public ResponseEntity<Void> activate(@PathVariable Long id) {
        log.info("PATCH /users/{}/activate", id);
        userService.activate(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/deactivate")
    @PreAuthorize("hasAuthority('PERM_USER_MANAGE')")
    public ResponseEntity<Void> deactivate(@PathVariable Long id) {
        log.info("PATCH /users/{}/deactivate", id);
        userService.deactivate(id);
        return ResponseEntity.noContent().build();
    }

    // ── Self-service profile (/me) ────────────────────────────────────────────

    @GetMapping("/me")
    public ResponseEntity<UserProfileResponse> getMyProfile(
            @AuthenticationPrincipal CaseFlowUserDetails principal) {
        log.info("GET /users/me — userId: {}", principal.getUserId());
        return ResponseEntity.ok(
                userMapper.toProfileResponse(userService.getMyProfile(principal.getUserId())));
    }

    @PutMapping("/me")
    public ResponseEntity<UserProfileResponse> updateMyProfile(
            @AuthenticationPrincipal CaseFlowUserDetails principal,
            @Valid @RequestBody UpdateProfileRequest request) {
        log.info("PUT /users/me — userId: {}", principal.getUserId());
        return ResponseEntity.ok(
                userMapper.toProfileResponse(userService.updateMyProfile(principal.getUserId(), request)));
    }

    @PostMapping("/me/change-password")
    public ResponseEntity<Void> changePassword(
            @AuthenticationPrincipal CaseFlowUserDetails principal,
            @Valid @RequestBody ChangePasswordRequest request) {
        log.info("POST /users/me/change-password — userId: {}", principal.getUserId());
        userService.changePassword(principal.getUserId(), request);
        return ResponseEntity.noContent().build();
    }
}
