package com.caseflow.identity.service;

import com.caseflow.common.exception.RoleNotFoundException;
import com.caseflow.common.exception.UserNotFoundException;
import com.caseflow.identity.api.dto.CreateUserRequest;
import com.caseflow.identity.api.dto.UpdateUserRequest;
import com.caseflow.identity.domain.Group;
import com.caseflow.identity.domain.Role;
import com.caseflow.identity.domain.User;
import com.caseflow.identity.repository.GroupRepository;
import com.caseflow.identity.repository.RoleRepository;
import com.caseflow.identity.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final GroupRepository groupRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository,
                       RoleRepository roleRepository,
                       GroupRepository groupRepository,
                       PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.groupRepository = groupRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public User createUser(CreateUserRequest request) {
        log.info("Creating user — username: '{}'", request.username());
        Role role = resolveRole(request.roleId());
        User user = new User();
        user.setUsername(request.username());
        user.setEmail(request.email());
        user.setFullName(request.fullName());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(role);
        user.setIsActive(request.isActive() != null ? request.isActive() : Boolean.TRUE);

        if (request.groupIds() != null && !request.groupIds().isEmpty()) {
            List<Group> groups = groupRepository.findAllById(request.groupIds());
            user.getGroups().addAll(groups);
        }

        User saved = userRepository.save(user);
        log.info("User created — userId: {}, username: '{}'", saved.getId(), request.username());
        return saved;
    }

    @Transactional
    public User updateUser(Long userId, UpdateUserRequest request) {
        log.info("Updating user {}", userId);
        User user = findOrThrow(userId);
        Role role = resolveRole(request.roleId());
        user.setEmail(request.email());
        user.setFullName(request.fullName());
        user.setRole(role);
        user.setIsActive(request.isActive());

        if (request.password() != null && !request.password().isBlank()) {
            user.setPasswordHash(passwordEncoder.encode(request.password()));
            log.info("Password updated for user {}", userId);
        }

        if (request.groupIds() != null) {
            user.getGroups().clear();
            if (!request.groupIds().isEmpty()) {
                List<Group> groups = groupRepository.findAllById(request.groupIds());
                user.getGroups().addAll(groups);
            }
            log.info("Groups updated for user {} — {} group(s)", userId, user.getGroups().size());
        }

        User saved = userRepository.save(user);
        log.info("User {} updated", userId);
        return saved;
    }

    @Transactional
    public void activate(Long userId) {
        log.info("Activating user {}", userId);
        User user = findOrThrow(userId);
        user.setIsActive(true);
        userRepository.save(user);
        log.info("User {} activated", userId);
    }

    @Transactional
    public void deactivate(Long userId) {
        log.info("Deactivating user {}", userId);
        User user = findOrThrow(userId);
        user.setIsActive(false);
        userRepository.save(user);
        log.info("User {} deactivated", userId);
    }

    @Transactional(readOnly = true)
    public User getById(Long userId) {
        return userRepository.findByIdWithRole(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
    }

    @Transactional(readOnly = true)
    public Optional<User> findByUsername(String username) {
        return userRepository.findByUsernameWithRoleAndGroups(username);
    }

    @Transactional(readOnly = true)
    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmailWithRoleAndGroups(email);
    }

    @Transactional(readOnly = true)
    public List<User> findAll() {
        return userRepository.findAllWithRole();
    }

    // ── Private ───────────────────────────────────────────────────────────────

    private Role resolveRole(Long roleId) {
        return roleRepository.findById(roleId)
                .orElseThrow(() -> new RoleNotFoundException(roleId));
    }

    private User findOrThrow(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
    }
}
