package com.caseflow.ticket.repository;

import com.caseflow.ticket.domain.Tag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TagRepository extends JpaRepository<Tag, Long> {

    Optional<Tag> findByCode(String code);

    boolean existsByCode(String code);

    List<Tag> findAllByOrderByCodeAsc();

    List<Tag> findAllByIsActiveTrueOrderByCodeAsc();
}
