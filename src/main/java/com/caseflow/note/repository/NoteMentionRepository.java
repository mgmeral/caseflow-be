package com.caseflow.note.repository;

import com.caseflow.note.domain.NoteMention;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface NoteMentionRepository extends JpaRepository<NoteMention, Long> {

    List<NoteMention> findByNoteId(Long noteId);

    List<NoteMention> findByNoteIdIn(List<Long> noteIds);

    @Query("SELECT m FROM NoteMention m WHERE m.noteId IN :noteIds")
    List<NoteMention> findAllByNoteIds(@Param("noteIds") List<Long> noteIds);
}
