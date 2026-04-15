package com.caseflow.sla.repository;

import com.caseflow.sla.domain.SlaPolicyConfig;
import com.caseflow.sla.domain.SlaScope;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SlaPolicyConfigRepository extends JpaRepository<SlaPolicyConfig, Long> {

    List<SlaPolicyConfig> findByIsActiveTrueOrderByScopeAscPriorityAsc();

    Optional<SlaPolicyConfig> findByScopeAndIsActiveTrue(SlaScope scope);

    Optional<SlaPolicyConfig> findByScopeAndPriorityAndIsActiveTrue(SlaScope scope, String priority);

    List<SlaPolicyConfig> findAllByOrderByScopeAscPriorityAsc();
}
