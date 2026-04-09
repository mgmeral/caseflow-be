package com.caseflow.email.api.dto;

import com.caseflow.email.domain.CursorResetMode;
import jakarta.validation.constraints.NotNull;

/**
 * Request body for the {@code POST /api/admin/mailboxes/{id}/reset-cursor} endpoint.
 *
 * @param mode        the reset strategy; required
 * @param explicitUid target UID value; required when {@code mode = SET_EXPLICIT_UID}
 */
public record ResetCursorRequest(
        @NotNull CursorResetMode mode,
        Long explicitUid
) {}
