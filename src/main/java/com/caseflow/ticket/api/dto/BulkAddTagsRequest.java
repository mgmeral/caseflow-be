package com.caseflow.ticket.api.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Request body for bulk tag addition. Tags are added idempotently (duplicates silently skipped). */
public record BulkAddTagsRequest(

        @NotEmpty
        @Size(max = 100)
        List<Long> ticketIds,

        @NotEmpty
        @Size(max = 20)
        List<Long> tagIds
) {}
