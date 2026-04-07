package com.caseflow.email.scheduled.api.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record ScheduleEmailRequest(
        @NotNull Long mailboxId,
        @NotBlank @Size(max = 512) String toAddress,
        @NotBlank @Size(max = 2000) String subject,
        @NotBlank String textBody,
        String htmlBody,
        @NotNull @Future Instant sendNotBefore
) {}
