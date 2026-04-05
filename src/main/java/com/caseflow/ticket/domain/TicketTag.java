package com.caseflow.ticket.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/**
 * Many-to-many link between a ticket and a tag.
 *
 * <p>The composite primary key (ticketId, tagId) prevents duplicate assignments.
 * Removing a tag removes only this link — the Tag definition is preserved.
 */
@Entity
@Table(name = "ticket_tags")
@IdClass(TicketTag.TicketTagId.class)
public class TicketTag {

    @Id
    @Column(name = "ticket_id")
    private Long ticketId;

    @Id
    @Column(name = "tag_id")
    private Long tagId;

    @Column(name = "tagged_at", nullable = false, updatable = false)
    private Instant taggedAt;

    @Column(name = "tagged_by")
    private Long taggedBy;

    @PrePersist
    private void onCreate() {
        if (taggedAt == null) taggedAt = Instant.now();
    }

    public Long getTicketId() { return ticketId; }
    public void setTicketId(Long ticketId) { this.ticketId = ticketId; }

    public Long getTagId() { return tagId; }
    public void setTagId(Long tagId) { this.tagId = tagId; }

    public Instant getTaggedAt() { return taggedAt; }

    public Long getTaggedBy() { return taggedBy; }
    public void setTaggedBy(Long taggedBy) { this.taggedBy = taggedBy; }

    // ── Composite PK ──────────────────────────────────────────────────────────

    public static class TicketTagId implements Serializable {
        private Long ticketId;
        private Long tagId;

        public TicketTagId() {}

        public TicketTagId(Long ticketId, Long tagId) {
            this.ticketId = ticketId;
            this.tagId = tagId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof TicketTagId that)) return false;
            return Objects.equals(ticketId, that.ticketId) && Objects.equals(tagId, that.tagId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(ticketId, tagId);
        }
    }
}
