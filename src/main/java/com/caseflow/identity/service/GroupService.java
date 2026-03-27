package com.caseflow.identity.service;

import com.caseflow.common.exception.GroupNotFoundException;
import com.caseflow.identity.domain.Group;
import com.caseflow.identity.domain.GroupType;
import com.caseflow.identity.repository.GroupRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class GroupService {

    private final GroupRepository groupRepository;

    public GroupService(GroupRepository groupRepository) {
        this.groupRepository = groupRepository;
    }

    @Transactional
    public Group createGroup(String name, GroupType type) {
        Group group = new Group();
        group.setName(name);
        group.setType(type);
        group.setIsActive(true);
        return groupRepository.save(group);
    }

    @Transactional
    public Group updateGroup(Long groupId, String name, GroupType type) {
        Group group = findOrThrow(groupId);
        group.setName(name);
        group.setType(type);
        return groupRepository.save(group);
    }

    @Transactional
    public void activate(Long groupId) {
        Group group = findOrThrow(groupId);
        group.setIsActive(true);
        groupRepository.save(group);
    }

    @Transactional
    public void deactivate(Long groupId) {
        Group group = findOrThrow(groupId);
        group.setIsActive(false);
        groupRepository.save(group);
    }

    @Transactional(readOnly = true)
    public Group getById(Long groupId) {
        return findOrThrow(groupId);
    }

    @Transactional(readOnly = true)
    public List<Group> findByType(GroupType type) {
        return groupRepository.findByType(type);
    }

    @Transactional(readOnly = true)
    public List<Group> findActiveGroups() {
        return groupRepository.findByIsActiveTrue();
    }

    private Group findOrThrow(Long groupId) {
        return groupRepository.findById(groupId)
                .orElseThrow(() -> new GroupNotFoundException(groupId));
    }
}
