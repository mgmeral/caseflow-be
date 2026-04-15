package com.caseflow.ticket.api.dto;

/**
 * Backlog aging distribution — how long open tickets have been waiting.
 *
 * <h2>Bucket definitions</h2>
 * Age is computed as time since ticket creation for open (non-terminal) tickets.
 * <ul>
 *   <li>{@code under4h} — &lt; 4 hours</li>
 *   <li>{@code h4to24} — 4–24 hours</li>
 *   <li>{@code d1to3} — 1–3 days</li>
 *   <li>{@code d3to7} — 3–7 days</li>
 *   <li>{@code over7d} — &gt; 7 days</li>
 * </ul>
 */
public record AgingBucketsResponse(
        long under4h,
        long h4to24,
        long d1to3,
        long d3to7,
        long over7d,
        long total
) {}
