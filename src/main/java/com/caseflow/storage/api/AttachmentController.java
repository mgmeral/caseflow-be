package com.caseflow.storage.api;

import com.caseflow.storage.service.AttachmentService;
import com.caseflow.ticket.api.dto.AttachmentMetadataResponse;
import com.caseflow.ticket.api.mapper.AttachmentMetadataMapper;
import com.caseflow.ticket.domain.AttachmentMetadata;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.UUID;

@Tag(name = "Attachments", description = "Attachment upload and download")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/attachments")
public class AttachmentController {

    private static final long MAX_BYTES = 25 * 1024 * 1024L; // 25 MB

    private final AttachmentService attachmentService;
    private final AttachmentMetadataMapper attachmentMetadataMapper;

    public AttachmentController(AttachmentService attachmentService,
                                AttachmentMetadataMapper attachmentMetadataMapper) {
        this.attachmentService = attachmentService;
        this.attachmentMetadataMapper = attachmentMetadataMapper;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AttachmentMetadataResponse> upload(
            @RequestParam Long ticketId,
            @RequestParam MultipartFile file) throws IOException {

        if (file.isEmpty()) {
            throw new IllegalArgumentException("File must not be empty");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new IllegalArgumentException("File size exceeds limit of 25 MB");
        }

        String contentType = file.getContentType() != null ? file.getContentType() : "application/octet-stream";
        String safeFileName = sanitize(file.getOriginalFilename());
        String objectKey = "tickets/" + ticketId + "/" + UUID.randomUUID() + "_" + safeFileName;

        AttachmentMetadata metadata = attachmentService.upload(
                ticketId, null, file.getOriginalFilename(), objectKey, contentType, file.getBytes());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(attachmentMetadataMapper.toResponse(metadata));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AttachmentMetadataResponse> getMetadata(@PathVariable Long id) {
        return ResponseEntity.ok(attachmentMetadataMapper.toResponse(attachmentService.getById(id)));
    }

    @GetMapping("/by-ticket/{ticketId}")
    public ResponseEntity<List<AttachmentMetadataResponse>> getByTicket(@PathVariable Long ticketId) {
        return ResponseEntity.ok(
                attachmentMetadataMapper.toResponseList(attachmentService.findByTicketId(ticketId)));
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<InputStreamResource> download(@PathVariable Long id) {
        AttachmentMetadata metadata = attachmentService.getById(id);
        InputStream stream = attachmentService.download(metadata.getObjectKey());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(metadata.getContentType()));
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(metadata.getFileName())
                .build());
        headers.setContentLength(metadata.getSize());

        return ResponseEntity.ok().headers(headers).body(new InputStreamResource(stream));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        attachmentService.delete(id);
        return ResponseEntity.noContent().build();
    }

    private String sanitize(String name) {
        if (name == null) return "file";
        return name.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
