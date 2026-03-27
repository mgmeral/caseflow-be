package com.caseflow.identity.repository;

import com.caseflow.identity.domain.Group;
import com.caseflow.identity.domain.GroupType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GroupRepository extends JpaRepository<Group, Long> {

    Optional<Group> findByName(String name);

    List<Group> findByType(GroupType type);

    List<Group> findByIsActiveTrue();

    boolean existsByName(String name);
}
