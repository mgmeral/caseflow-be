package com.caseflow.ai.service;

import com.caseflow.ai.domain.AiSyncStatus;
import com.caseflow.ai.domain.TicketAiIndex;
import com.caseflow.ai.repository.TicketAiIndexRepository;
import com.caseflow.ai.repository.TicketAiResponseCacheRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Manages the AI source version for tickets.
 *
 * <p>The source version increments only when AI-relevant ticket data changes:
 * <ul>
 *   <li>Inbound or outbound email added</li>
 *   <li>Internal note added</li>
 *   <li>Ticket resolved or closed</li>
 *   <li>Tags changed</li>
 *   <li>Resolution summary changed</li>
 * </ul>
 *
 * <p>Version increments are NOT triggered by cosmetic changes like status transitions
 * between intermediate states, priority changes, or assignment changes.
 *
 * <p>On increment, response cache entries for the ticket are invalidated.
 */
@Service
public class AiSourceVersionService {

    private static final Logger log = LoggerFactory.getLogger(AiSourceVersionService.class);

    private final TicketAiIndexRepository indexRepository;
    private final TicketAiResponseCacheRepository cacheRepository;

    public AiSourceVersionService(TicketAiIndexRepository indexRepository,
                                   TicketAiResponseCacheRepository cacheRepository) {
        this.indexRepository = indexRepository;
        this.cacheRepository = cacheRepository;
    }

    /**
     * Increments the source version for the given ticket.
     * Creates a new index row if one does not exist yet.
     * Invalidates all cached AI responses for the ticket.
     *
     * @param ticketId the ticket whose AI-relevant content changed
     */
    @Transactional
    public void onAiRelevantChange(Long ticketId) {
        int updated = indexRepository.incrementSourceVersion(ticketId);
        if (updated == 0) {
            // No row yet — create initial entry
            TicketAiIndex index = new TicketAiIndex();
            index.setTicketId(ticketId);
            index.setSourceVersion(1L);
            index.setIndexedVersion(0L);
            index.setSyncStatus(AiSyncStatus.STALE);
            indexRepository.save(index);
            log.debug("Created initial TicketAiIndex for ticketId={}", ticketId);
        } else {
            log.debug("Incremented AI source version for ticketId={}", ticketId);
        }

        // Invalidate cached AI responses — they are now stale
        int invalidated = cacheRepository.invalidateByTicketId(ticketId);
        if (invalidated > 0) {
            log.debug("Invalidated {} AI cache entries for ticketId={}", invalidated, ticketId);
        }
    }

    /**
     * Ensures a TicketAiIndex row exists for the ticket with version 1.
     * Safe to call multiple times — no-ops if the row already exists.
     */
    @Transactional
    public void ensureIndexExists(Long ticketId) {
        if (indexRepository.findByTicketId(ticketId).isEmpty()) {
            TicketAiIndex index = new TicketAiIndex();
            index.setTicketId(ticketId);
            index.setSourceVersion(1L);
            index.setIndexedVersion(0L);
            index.setSyncStatus(AiSyncStatus.PENDING);
            indexRepository.save(index);
            log.debug("Created initial TicketAiIndex for ticketId={}", ticketId);
        }
    }

    /**
     * Returns the current source version for a ticket, or 0 if no index row exists.
     */
    @Transactional(readOnly = true)
    public long currentSourceVersion(Long ticketId) {
        return indexRepository.findByTicketId(ticketId)
                .map(TicketAiIndex::getSourceVersion)
                .orElse(0L);
    }
}
