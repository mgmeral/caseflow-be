package com.caseflow.ticket.api.dto;

/**
 * Which date field to use for filtering/grouping in reports.
 */
public enum DateDimension {
    /** Filter by ticket creation time. Default. */
    CREATED,
    /** Filter by ticket last-updated time. */
    UPDATED,
    /** Filter by ticket resolved time ({@code resolvedAt}). */
    RESOLVED,
    /** Filter by ticket closed time ({@code closedAt}). */
    CLOSED
}
