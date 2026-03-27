package com.caseflow.workflow.repository;

import com.caseflow.workflow.domain.Assignment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AssignmentRepository extends JpaRepository<Assignment, Long> {

    Optional<Assignment> findByTicketIdAndUnassignedAtIsNull(Long ticketId);

    List<Assignment> findByTicketIdOrderByAssignedAtAsc(Long ticketId);
}
