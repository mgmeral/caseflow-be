package com.caseflow.email.scheduled.api.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * Request to schedule an outbound email for future delivery.
 *
 * <h2>Recipient resolution</h2>
 * Either {@code sourceEventId} or {@code toAddress} must be provided:
 * <ul>
 *   <li>When {@code sourceEventId} is set, the backend resolves the recipient from the inbound
 *       event's Reply-To or From header and populates RFC 2822 In-Reply-To / References headers.
 *       {@code toAddress} is used as an override only when also provided.</li>
 *   <li>When no {@code sourceEventId}, {@code toAddress} is required (proactive outreach).</li>
 * </ul>
 */
public record ScheduleEmailRequest(

        @NotNull
        Long mailboxId,

        /** Inbound event this scheduled email replies to. If set, reply-target and threading are derived. */
        Long sourceEventId,

        /**
         * Explicit recipient address. Required when {@code sourceEventId} is absent.
         * When {@code sourceEventId} is set, this overrides the derived address.
         */
        @Size(max = 512)
        String toAddress,

        @NotBlank
        @Size(max = 2000)
        String subject,

        @NotBlank
        String textBody,

        String htmlBody,

        @NotNull
        @Future
        Instant sendNotBefore,

        /** Optional — if set, overrides templateCode and the default CUSTOMER_REPLY template. */
        Long templateId,

        /** Optional — if set, used to look up an active template by code; ignored when templateId set. */
        String templateCode,

        /** True when the agent modified preview-rendered content before scheduling. Defaults to false. */
        Boolean contentWasEdited
) {}
