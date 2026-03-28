package com.caseflow.identity.service;

import com.caseflow.common.exception.GroupTypeNotFoundException;
import com.caseflow.identity.api.dto.CreateGroupTypeRequest;
import com.caseflow.identity.api.dto.UpdateGroupTypeRequest;
import com.caseflow.identity.domain.GroupType;
import com.caseflow.identity.repository.GroupTypeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class GroupTypeService {

    private static final Logger log = LoggerFactory.getLogger(GroupTypeService.class);

    private final GroupTypeRepository groupTypeRepository;

    public GroupTypeService(GroupTypeRepository groupTypeRepository) {
        this.groupTypeRepository = groupTypeRepository;
    }

    @Transactional
    public GroupType createGroupType(CreateGroupTypeRequest request) {
        log.info("Creating group type — code: '{}', name: '{}'", request.code(), request.name());
        GroupType groupType = new GroupType();
        groupType.setCode(request.code().toUpperCase());
        groupType.setName(request.name());
        groupType.setDescription(request.description());
        groupType.setIsActive(true);
        GroupType saved = groupTypeRepository.save(groupType);
        log.info("Group type created — id: {}, code: '{}'", saved.getId(), saved.getCode());
        return saved;
    }

    @Transactional
    public GroupType updateGroupType(Long groupTypeId, UpdateGroupTypeRequest request) {
        log.info("Updating group type {} — code: '{}', name: '{}'", groupTypeId, request.code(), request.name());
        GroupType groupType = findOrThrow(groupTypeId);
        groupType.setCode(request.code().toUpperCase());
        groupType.setName(request.name());
        groupType.setDescription(request.description());
        GroupType saved = groupTypeRepository.save(groupType);
        log.info("Group type {} updated", groupTypeId);
        return saved;
    }

    @Transactional
    public void activate(Long groupTypeId) {
        log.info("Activating group type {}", groupTypeId);
        GroupType groupType = findOrThrow(groupTypeId);
        groupType.setIsActive(true);
        groupTypeRepository.save(groupType);
        log.info("Group type {} activated", groupTypeId);
    }

    @Transactional
    public void deactivate(Long groupTypeId) {
        log.info("Deactivating group type {}", groupTypeId);
        GroupType groupType = findOrThrow(groupTypeId);
        groupType.setIsActive(false);
        groupTypeRepository.save(groupType);
        log.info("Group type {} deactivated", groupTypeId);
    }

    @Transactional(readOnly = true)
    public GroupType getById(Long groupTypeId) {
        return findOrThrow(groupTypeId);
    }

    @Transactional(readOnly = true)
    public List<GroupType> findActiveGroupTypes() {
        return groupTypeRepository.findByIsActiveTrue();
    }

    // ── Private ───────────────────────────────────────────────────────────────

    private GroupType findOrThrow(Long groupTypeId) {
        return groupTypeRepository.findById(groupTypeId)
                .orElseThrow(() -> new GroupTypeNotFoundException(groupTypeId));
    }
}
