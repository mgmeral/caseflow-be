package com.caseflow.identity.api;

import com.caseflow.identity.api.dto.CreateUserRequest;
import com.caseflow.identity.api.dto.UpdateUserRequest;
import com.caseflow.identity.api.dto.UserResponse;
import com.caseflow.identity.api.dto.UserSummaryResponse;
import com.caseflow.identity.api.mapper.UserMapper;
import com.caseflow.identity.service.UserService;
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

@Tag(name = "Users", description = "User account management")
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;
    private final UserMapper userMapper;

    public UserController(UserService userService, UserMapper userMapper) {
        this.userService = userService;
        this.userMapper = userMapper;
    }

    @PostMapping
    public ResponseEntity<UserResponse> createUser(@Valid @RequestBody CreateUserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                userMapper.toResponse(userService.createUser(request.username(), request.email(), request.fullName()))
        );
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(userMapper.toResponse(userService.getById(id)));
    }

    @GetMapping
    public ResponseEntity<List<UserSummaryResponse>> listUsers() {
        return ResponseEntity.ok(
                userService.findAll().stream().map(userMapper::toSummaryResponse).toList()
        );
    }

    @GetMapping("/by-username")
    public ResponseEntity<UserResponse> getByUsername(@RequestParam String username) {
        return userService.findByUsername(username)
                .map(user -> ResponseEntity.ok(userMapper.toResponse(user)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/by-email")
    public ResponseEntity<UserResponse> getByEmail(@RequestParam String email) {
        return userService.findByEmail(email)
                .map(user -> ResponseEntity.ok(userMapper.toResponse(user)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<UserResponse> updateUser(@PathVariable Long id,
                                                   @Valid @RequestBody UpdateUserRequest request) {
        return ResponseEntity.ok(
                userMapper.toResponse(userService.updateUser(id, request.email(), request.fullName()))
        );
    }

    @PatchMapping("/{id}/activate")
    public ResponseEntity<Void> activate(@PathVariable Long id) {
        userService.activate(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/deactivate")
    public ResponseEntity<Void> deactivate(@PathVariable Long id) {
        userService.deactivate(id);
        return ResponseEntity.noContent().build();
    }
}
