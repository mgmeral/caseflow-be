package com.caseflow.identity.service;

import com.caseflow.common.exception.AdminLockoutException;
import com.caseflow.common.exception.RoleNotFoundException;
import com.caseflow.identity.api.dto.CreateRoleRequest;
import com.caseflow.identity.api.dto.UpdateRoleRequest;
import com.caseflow.identity.domain.Permission;
import com.caseflow.identity.domain.Role;
import com.caseflow.identity.domain.TicketScope;
import com.caseflow.identity.repository.RoleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class RoleService {

    private static final Logger log = LoggerFactory.getLogger(RoleService.class);

    /**
     * The permissions that together constitute effective admin capability.
     * A user must hold ALL of these to be counted as a system admin.
     */
    private static final Set<Permission> ADMIN_CAPABILITY =
            Set.of(Permission.USER_MANAGE, Permission.ROLE_MANAGE);

    private final RoleRepository roleRepository;

    public RoleService(RoleRepository roleRepository) {
        this.roleRepository = roleRepository;
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Role getById(Long roleId) {
        return findOrThrow(roleId);
    }

    @Transactional(readOnly = true)
    public List<Role> findAll() {
        return roleRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<Permission> getPermissionCatalog() {
        return Arrays.asList(Permission.values());
    }

    // ── Write ─────────────────────────────────────────────────────────────────

    @Transactional
    public Role createRole(CreateRoleRequest request) {
        log.info("Creating role — code: '{}', name: '{}'", request.code(), request.name());
        Role role = new Role();
        role.setCode(request.code().toUpperCase());
        role.setName(request.name());
        role.setDescription(request.description());
        role.setTicketScope(request.ticketScope() != null ? request.ticketScope() : TicketScope.ALL);
        role.setIsActive(true);
        Set<Permission> perms = request.permissions() != null
                ? new LinkedHashSet<>(request.permissions())
                : new LinkedHashSet<>();
        role.setPermissions(perms);
        Role saved = roleRepository.save(role);
        log.info("Role created — id: {}, code: '{}'", saved.getId(), saved.getCode());
        return saved;
    }

    @Transactional
    public Role updateRole(Long roleId, UpdateRoleRequest request) {
        log.info("Updating role {} — code: '{}', permissions: {}", roleId, request.code(), request.permissions());
        Role role = findOrThrow(roleId);

        Set<Permission> proposed = new LinkedHashSet<>(request.permissions());

        // If the role currently has admin capability and the update would remove it, check safety
        if (hasAdminCapability(role.getPermissions()) && !hasAdminCapability(proposed)) {
            assertAdminCapabilityPreservedExcluding(roleId);
        }

        role.setCode(request.code().toUpperCase());
        role.setName(request.name());
        role.setDescription(request.description());
        role.setTicketScope(request.ticketScope());
        role.setPermissions(proposed);
        Role saved = roleRepository.save(role);
        log.info("Role {} updated", roleId);
        return saved;
    }

    @Transactional
    public void activate(Long roleId) {
        log.info("Activating role {}", roleId);
        Role role = findOrThrow(roleId);
        role.setIsActive(true);
        roleRepository.save(role);
        log.info("Role {} activated", roleId);
    }

    @Transactional
    public void deactivate(Long roleId) {
        log.info("Deactivating role {}", roleId);
        Role role = findOrThrow(roleId);

        // Deactivating this role removes it from the effective admin pool
        if (hasAdminCapability(role.getPermissions())) {
            assertAdminCapabilityPreservedExcluding(roleId);
        }

        role.setIsActive(false);
        roleRepository.save(role);
        log.info("Role {} deactivated", roleId);
    }

    @Transactional
    public void deleteRole(Long roleId) {
        log.info("Deleting role {}", roleId);
        Role role = findOrThrow(roleId);

        if (hasAdminCapability(role.getPermissions())) {
            assertAdminCapabilityPreservedExcluding(roleId);
        }

        roleRepository.delete(role);
        log.info("Role {} deleted", roleId);
    }

    // ── Safety ────────────────────────────────────────────────────────────────

    /**
     * Verifies that after removing {@code roleId} from the effective admin pool,
     * at least one active user with an active role still holds USER_MANAGE + ROLE_MANAGE.
     * Throws {@link AdminLockoutException} if the system would be locked out.
     */
    private void assertAdminCapabilityPreservedExcluding(Long roleId) {
        long remainingAdmins = roleRepository.countActiveAdminUsersExcludingRole(roleId);
        if (remainingAdmins == 0) {
            throw new AdminLockoutException(
                    "Operation rejected: this would leave the system with no active user capable " +
                    "of managing roles and users (USER_MANAGE + ROLE_MANAGE required).");
        }
    }

    private boolean hasAdminCapability(Set<Permission> permissions) {
        return permissions.containsAll(ADMIN_CAPABILITY);
    }

    // ── Private ───────────────────────────────────────────────────────────────

    private Role findOrThrow(Long roleId) {
        return roleRepository.findById(roleId)
                .orElseThrow(() -> new RoleNotFoundException(roleId));
    }
}
