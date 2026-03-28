package com.caseflow.identity.service;

import com.caseflow.common.exception.GroupNotFoundException;
import com.caseflow.common.exception.GroupTypeNotFoundException;
import com.caseflow.identity.api.dto.CreateGroupRequest;
import com.caseflow.identity.api.dto.UpdateGroupRequest;
import com.caseflow.identity.domain.Group;
import com.caseflow.identity.domain.GroupType;
import com.caseflow.identity.domain.User;
import com.caseflow.identity.repository.GroupRepository;
import com.caseflow.identity.repository.GroupTypeRepository;
import com.caseflow.identity.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class GroupService {

    private static final Logger log = LoggerFactory.getLogger(GroupService.class);

    private final GroupRepository groupRepository;
    private final GroupTypeRepository groupTypeRepository;
    private final UserRepository userRepository;

    public GroupService(GroupRepository groupRepository,
                        GroupTypeRepository groupTypeRepository,
                        UserRepository userRepository) {
        this.groupRepository = groupRepository;
        this.groupTypeRepository = groupTypeRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public Group createGroup(CreateGroupRequest request) {
        log.info("Creating group — name: '{}', groupTypeId: {}", request.name(), request.groupTypeId());
        GroupType groupType = resolveGroupType(request.groupTypeId());
        Group group = new Group();
        group.setName(request.name());
        group.setGroupType(groupType);
        group.setDescription(request.description());
        group.setIsActive(true);
        Group saved = groupRepository.save(group);

        // Group.users is the inverse side (mappedBy = "groups"); membership must
        // be set through User.groups (the owner side) to be persisted.
        if (request.userIds() != null && !request.userIds().isEmpty()) {
            List<User> users = userRepository.findAllById(request.userIds());
            for (User u : users) {
                u.getGroups().add(saved);
                userRepository.save(u);
            }
            log.info("Group {} created with {} initial member(s)", saved.getId(), users.size());
        } else {
            log.info("Group created — groupId: {}, name: '{}'", saved.getId(), request.name());
        }

        return findOrThrow(saved.getId());
    }

    @Transactional
    public Group updateGroup(Long groupId, UpdateGroupRequest request) {
        log.info("Updating group {} — name: '{}', groupTypeId: {}", groupId, request.name(), request.groupTypeId());
        Group group = findOrThrow(groupId);
        GroupType groupType = resolveGroupType(request.groupTypeId());
        group.setName(request.name());
        group.setGroupType(groupType);
        group.setDescription(request.description());
        groupRepository.save(group);

        if (request.userIds() != null) {
            applyMembershipChange(group, request.userIds());
            log.info("Group {} members updated — {} member(s)", groupId, request.userIds().size());
        }

        log.info("Group {} updated", groupId);
        return findOrThrow(groupId);
    }

    @Transactional
    public void activate(Long groupId) {
        log.info("Activating group {}", groupId);
        Group group = findOrThrow(groupId);
        group.setIsActive(true);
        groupRepository.save(group);
        log.info("Group {} activated", groupId);
    }

    @Transactional
    public void deactivate(Long groupId) {
        log.info("Deactivating group {}", groupId);
        Group group = findOrThrow(groupId);
        group.setIsActive(false);
        groupRepository.save(group);
        log.info("Group {} deactivated", groupId);
    }

    @Transactional(readOnly = true)
    public Group getById(Long groupId) {
        return findOrThrow(groupId);
    }

    @Transactional(readOnly = true)
    public List<Group> findActiveGroups() {
        return groupRepository.findByIsActiveTrue();
    }

    // ── Private ───────────────────────────────────────────────────────────────

    private GroupType resolveGroupType(Long groupTypeId) {
        return groupTypeRepository.findById(groupTypeId)
                .orElseThrow(() -> new GroupTypeNotFoundException(groupTypeId));
    }

    /**
     * Replaces the group's member list with {@code desiredUserIds}.
     * Operates through User.groups (owner side) because Group.users is mappedBy.
     */
    private void applyMembershipChange(Group group, List<Long> desiredUserIds) {
        Set<Long> desired = Set.copyOf(desiredUserIds);
        Set<Long> current = group.getUsers().stream()
                .map(User::getId)
                .collect(Collectors.toSet());

        Set<Long> toRemove = current.stream().filter(id -> !desired.contains(id)).collect(Collectors.toSet());
        Set<Long> toAdd    = desired.stream().filter(id -> !current.contains(id)).collect(Collectors.toSet());

        if (!toRemove.isEmpty()) {
            for (User u : userRepository.findAllById(toRemove)) {
                // removeIf with ID comparison because Group does not override equals/hashCode
                u.getGroups().removeIf(g -> g.getId().equals(group.getId()));
                userRepository.save(u);
            }
        }
        if (!toAdd.isEmpty()) {
            for (User u : userRepository.findAllById(toAdd)) {
                u.getGroups().add(group);
                userRepository.save(u);
            }
        }
    }

    private Group findOrThrow(Long groupId) {
        return groupRepository.findByIdWithMembers(groupId)
                .orElseThrow(() -> new GroupNotFoundException(groupId));
    }
}
