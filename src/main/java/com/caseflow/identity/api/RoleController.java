package com.caseflow.identity.api;

import com.caseflow.identity.api.dto.CreateRoleRequest;
import com.caseflow.identity.api.dto.RoleResponse;
import com.caseflow.identity.api.dto.RoleSummaryResponse;
import com.caseflow.identity.api.dto.UpdateRoleRequest;
import com.caseflow.identity.api.mapper.RoleMapper;
import com.caseflow.identity.domain.Permission;
import com.caseflow.identity.service.RoleService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Roles", description = "Dynamic role management and permission assignment")
@RestController
@RequestMapping("/api/roles")
public class RoleController {

    private static final Logger log = LoggerFactory.getLogger(RoleController.class);

    private final RoleService roleService;
    private final RoleMapper roleMapper;

    public RoleController(RoleService roleService, RoleMapper roleMapper) {
        this.roleService = roleService;
        this.roleMapper = roleMapper;
    }

    @GetMapping("/permissions")
    @PreAuthorize("hasAuthority('PERM_ROLE_MANAGE')")
    public ResponseEntity<List<String>> getPermissionCatalog() {
        log.info("GET /roles/permissions");
        List<String> codes = roleService.getPermissionCatalog().stream()
                .map(Permission::name)
                .toList();
        return ResponseEntity.ok(codes);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PERM_ROLE_MANAGE')")
    public ResponseEntity<List<RoleSummaryResponse>> listRoles() {
        return ResponseEntity.ok(
                roleService.findAll().stream().map(roleMapper::toSummaryResponse).toList()
        );
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_ROLE_MANAGE')")
    public ResponseEntity<RoleResponse> getById(@PathVariable Long id) {
        log.info("GET /roles/{}", id);
        return ResponseEntity.ok(roleMapper.toResponse(roleService.getById(id)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PERM_ROLE_MANAGE')")
    public ResponseEntity<RoleResponse> createRole(@Valid @RequestBody CreateRoleRequest request) {
        log.info("POST /roles — code: '{}', name: '{}'", request.code(), request.name());
        RoleResponse response = roleMapper.toResponse(roleService.createRole(request));
        log.info("POST /roles succeeded — id: {}", response.id());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_ROLE_MANAGE')")
    public ResponseEntity<RoleResponse> updateRole(@PathVariable Long id,
                                                   @Valid @RequestBody UpdateRoleRequest request) {
        log.info("PUT /roles/{} — code: '{}', permissions: {}", id, request.code(), request.permissions());
        RoleResponse response = roleMapper.toResponse(roleService.updateRole(id, request));
        log.info("PUT /roles/{} succeeded", id);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}/activate")
    @PreAuthorize("hasAuthority('PERM_ROLE_MANAGE')")
    public ResponseEntity<Void> activate(@PathVariable Long id) {
        log.info("PATCH /roles/{}/activate", id);
        roleService.activate(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/deactivate")
    @PreAuthorize("hasAuthority('PERM_ROLE_MANAGE')")
    public ResponseEntity<Void> deactivate(@PathVariable Long id) {
        log.info("PATCH /roles/{}/deactivate", id);
        roleService.deactivate(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_ROLE_MANAGE')")
    public ResponseEntity<Void> deleteRole(@PathVariable Long id) {
        log.info("DELETE /roles/{}", id);
        roleService.deleteRole(id);
        return ResponseEntity.noContent().build();
    }
}
