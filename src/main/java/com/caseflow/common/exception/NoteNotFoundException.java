package com.caseflow.common.exception;

public class NoteNotFoundException extends RuntimeException {

    public NoteNotFoundException(Long noteId) {
        super("Note not found: " + noteId);
    }
}
