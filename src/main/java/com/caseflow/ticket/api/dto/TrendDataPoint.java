package com.caseflow.ticket.api.dto;

/**
 * A single daily data point in a ticket trend series.
 *
 * @param date      ISO date string, e.g. "2026-04-15"
 * @param created   tickets created on this date
 * @param resolved  tickets resolved on this date
 * @param closed    tickets closed on this date
 * @param breached  tickets that breached SLA on this date (optional — 0 if not tracked)
 */
public record TrendDataPoint(
        String date,
        long created,
        long resolved,
        long closed,
        long breached
) {}
