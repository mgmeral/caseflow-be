package com.caseflow.ticket.api.dto;

import java.util.List;
import java.util.Map;

/**
 * Partial-success response for bulk ticket operations.
 *
 * <p>The caller should inspect {@code failedCount} before assuming full success.
 * Individual failure reasons are keyed by ticket ID in {@code failures}.
 */
public record BulkActionResult(

        /** Total number of tickets that were submitted in the request. */
        int requestedCount,

        /** Number of tickets the action was successfully applied to. */
        int succeededCount,

        /** Number of tickets the action could not be applied to. */
        int failedCount,

        /** IDs of successfully processed tickets. */
        List<Long> succeededIds,

        /** ticketId → human-readable failure reason. */
        Map<Long, String> failures
) {

    public static BulkActionResult of(List<Long> succeeded, Map<Long, String> failures) {
        int total = succeeded.size() + failures.size();
        return new BulkActionResult(total, succeeded.size(), failures.size(), succeeded, failures);
    }
}
