package com.caseflow.integration.jira.repository;

import com.caseflow.integration.jira.domain.TicketJiraLink;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TicketJiraLinkRepository extends JpaRepository<TicketJiraLink, Long> {

    Optional<TicketJiraLink> findByTicketId(Long ticketId);

    Optional<TicketJiraLink> findByTicketPublicId(UUID ticketPublicId);

    boolean existsByTicketId(Long ticketId);
}
