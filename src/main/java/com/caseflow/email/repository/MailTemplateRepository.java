package com.caseflow.email.repository;

import com.caseflow.email.domain.MailTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MailTemplateRepository extends JpaRepository<MailTemplate, Long> {

    Optional<MailTemplate> findByCode(String code);

    Optional<MailTemplate> findByCodeAndIsActiveTrue(String code);

    List<MailTemplate> findAllByOrderByCodeAsc();
}
