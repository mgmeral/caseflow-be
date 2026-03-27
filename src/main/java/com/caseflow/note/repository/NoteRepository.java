package com.caseflow.note.repository;

import com.caseflow.note.domain.Note;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NoteRepository extends JpaRepository<Note, Long> {

    List<Note> findByTicketIdOrderByCreatedAtAsc(Long ticketId);
}
