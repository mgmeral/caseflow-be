package com.caseflow.integration.jira.repository;

import com.caseflow.integration.jira.domain.JiraConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface JiraConfigRepository extends JpaRepository<JiraConfig, Long> {

    /** Returns the first (and only expected) Jira config. */
    Optional<JiraConfig> findFirstByOrderByIdAsc();
}
