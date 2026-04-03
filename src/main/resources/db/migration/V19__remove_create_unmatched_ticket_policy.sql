-- Enforce invariant: unmatched emails must never create tickets.
-- CREATE_UNMATCHED_TICKET is retired; any existing rows are migrated to MANUAL_REVIEW
-- so that they enter the QUARANTINED state and await operator review instead.

UPDATE email_mailboxes
   SET unknown_sender_policy = 'MANUAL_REVIEW'
 WHERE unknown_sender_policy = 'CREATE_UNMATCHED_TICKET';

UPDATE customer_email_settings
   SET unknown_sender_policy = 'MANUAL_REVIEW'
 WHERE unknown_sender_policy = 'CREATE_UNMATCHED_TICKET';

COMMENT ON COLUMN email_mailboxes.unknown_sender_policy IS
    'How to handle emails from senders not matched by any routing rule. '
    'Values match UnknownSenderPolicy enum: MANUAL_REVIEW, IGNORE, REJECT. '
    'No policy may create a ticket — ticket creation requires a routing rule or thread match.';
