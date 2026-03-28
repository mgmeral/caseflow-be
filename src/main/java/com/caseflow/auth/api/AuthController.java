package com.caseflow.auth.api;

import com.caseflow.auth.AuthService;
import com.caseflow.auth.CaseFlowUserDetails;
import com.caseflow.auth.api.dto.LoginRequest;
import com.caseflow.auth.api.dto.MeResponse;
import com.caseflow.auth.api.dto.RefreshTokenRequest;
import com.caseflow.auth.api.dto.TokenResponse;
import com.caseflow.identity.domain.Permission;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Auth", description = "Authentication — login, refresh, logout, me")
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @Operation(summary = "Login with username and password")
    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        log.info("POST /auth/login — username: {}", request.username());
        AuthService.TokenPair pair = authService.login(request.username(), request.password());
        log.info("POST /auth/login succeeded — username: {}", request.username());
        return ResponseEntity.ok(TokenResponse.of(pair.accessToken(), pair.refreshToken(), pair.expiresIn()));
    }

    @Operation(summary = "Refresh access token using a valid refresh token")
    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        log.info("POST /auth/refresh");
        AuthService.TokenPair pair = authService.refresh(request.refreshToken());
        log.info("POST /auth/refresh succeeded");
        return ResponseEntity.ok(TokenResponse.of(pair.accessToken(), pair.refreshToken(), pair.expiresIn()));
    }

    @Operation(summary = "Logout — revokes the provided refresh token")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshTokenRequest request) {
        log.info("POST /auth/logout");
        authService.logout(request.refreshToken());
        log.info("POST /auth/logout succeeded");
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Get current authenticated user — includes roleId, permissionCodes, ticketScope, groupIds")
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/me")
    public ResponseEntity<MeResponse> me(@AuthenticationPrincipal CaseFlowUserDetails currentUser) {
        log.info("GET /auth/me — userId: {}", currentUser.getUserId());
        return ResponseEntity.ok(new MeResponse(
                currentUser.getUserId(),
                currentUser.getUsername(),
                currentUser.getEmail(),
                currentUser.getFullName(),
                currentUser.getRoleId(),
                currentUser.getRoleCode(),
                currentUser.getRoleName(),
                currentUser.getPermissions().stream().map(Permission::name).sorted().toList(),
                currentUser.getTicketScope(),
                currentUser.getGroupIds()
        ));
    }
}
