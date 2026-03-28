package com.caseflow.identity.repository;

import com.caseflow.identity.domain.GroupType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GroupTypeRepository extends JpaRepository<GroupType, Long> {

    Optional<GroupType> findByCode(String code);

    List<GroupType> findByIsActiveTrue();

    boolean existsByCode(String code);
}
