package com.caseflow.note.api;

import com.caseflow.auth.JwtTokenService;
import com.caseflow.common.exception.NoteNotFoundException;
import com.caseflow.common.security.SecurityConfig;
import com.caseflow.note.api.dto.AddNoteRequest;
import com.caseflow.note.api.dto.NoteResponse;
import com.caseflow.note.api.mapper.NoteMapper;
import com.caseflow.note.domain.Note;
import com.caseflow.note.domain.NoteType;
import com.caseflow.note.service.NoteService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NoteController.class)
@Import(SecurityConfig.class)
class NoteControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private JwtTokenService jwtTokenService;

    @MockBean
    private NoteService noteService;

    @MockBean
    private NoteMapper noteMapper;

    @Test
    @WithMockUser(roles = "AGENT")
    void addNote_returns201_withValidRequest() throws Exception {
        AddNoteRequest request = new AddNoteRequest(10L, "Investigation started.", NoteType.INVESTIGATION);
        Note note = buildNote(1L, 10L);
        NoteResponse response = new NoteResponse(1L, 10L, "Investigation started.", NoteType.INVESTIGATION, 1L, Instant.now());

        when(noteService.addNote(any(), any(), any(), anyLong())).thenReturn(note);
        when(noteMapper.toResponse(note)).thenReturn(response);

        mockMvc.perform(post("/api/notes")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.type").value("INVESTIGATION"));
    }

    @Test
    @WithMockUser(roles = "AGENT")
    void addNote_returns400_whenContentIsBlank() throws Exception {
        AddNoteRequest request = new AddNoteRequest(10L, "", NoteType.INFO);

        mockMvc.perform(post("/api/notes")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @WithMockUser(roles = "VIEWER")
    void getById_returns200_whenFound() throws Exception {
        Note note = buildNote(5L, 10L);
        NoteResponse response = new NoteResponse(5L, 10L, "Some content.", NoteType.INFO, 1L, Instant.now());

        when(noteService.getById(5L)).thenReturn(note);
        when(noteMapper.toResponse(note)).thenReturn(response);

        mockMvc.perform(get("/api/notes/5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5));
    }

    @Test
    @WithMockUser(roles = "VIEWER")
    void getById_returns404_whenNotFound() throws Exception {
        when(noteService.getById(99L)).thenThrow(new NoteNotFoundException(99L));

        mockMvc.perform(get("/api/notes/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @WithMockUser(roles = "VIEWER")
    void getByTicket_returns200_withNoteList() throws Exception {
        Note n = buildNote(1L, 10L);
        NoteResponse r = new NoteResponse(1L, 10L, "Content.", NoteType.INFO, 1L, Instant.now());

        when(noteService.listByTicket(10L)).thenReturn(List.of(n));
        when(noteMapper.toResponseList(any())).thenReturn(List.of(r));

        mockMvc.perform(get("/api/notes/by-ticket/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].ticketId").value(10));
    }

    @Test
    void getByTicket_returns401_whenUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/notes/by-ticket/10"))
                .andExpect(status().isUnauthorized());
    }

    private Note buildNote(Long id, Long ticketId) {
        Note n = new Note();
        n.setTicketId(ticketId);
        n.setContent("Content.");
        n.setType(NoteType.INFO);
        n.setCreatedBy(1L);
        try {
            var f = Note.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(n, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return n;
    }
}
