package com.caseflow.email.repository;

import com.caseflow.email.domain.EmailMailbox;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EmailMailboxRepository extends JpaRepository<EmailMailbox, Long> {

    Optional<EmailMailbox> findByAddress(String address);

    List<EmailMailbox> findAllByIsActiveTrue();
}
