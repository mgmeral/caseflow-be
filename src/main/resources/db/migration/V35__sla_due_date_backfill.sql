-- V35: Backfill SLA due dates for non-terminal tickets that were created before
--      the SLA assignment fix (EmailIngressServiceImpl bypassed SLA stamping).
--
-- Scope:
--   - Only tickets where BOTH first_response_due_at AND resolution_due_at are NULL
--   - Only non-terminal tickets (RESOLVED and CLOSED tickets need no SLA clock)
--   - Applies today's active policy (same resolution order as SlaService.resolvePolicy):
--       1. PRIORITY scope policy matching the ticket's priority (if active)
--       2. GLOBAL fallback (if active)
--   - If no matching policy exists for a ticket, that ticket is left NULL (by design)
--
-- Safety:
--   - Idempotent: WHERE clause restricts to null-only rows; re-running has no effect
--   - Non-destructive: never overwrites existing non-null due dates
--   - If sla_policy_configs table is empty, no rows are updated

UPDATE tickets t
SET
    first_response_due_at = t.created_at
        + (COALESCE(
            (SELECT p.first_response_target_minutes
             FROM   sla_policy_configs p
             WHERE  p.scope = 'PRIORITY'
               AND  p.priority = t.priority
               AND  p.is_active = TRUE
             ORDER  BY p.id
             LIMIT  1),
            (SELECT p.first_response_target_minutes
             FROM   sla_policy_configs p
             WHERE  p.scope = 'GLOBAL'
               AND  p.is_active = TRUE
             ORDER  BY p.id
             LIMIT  1)
          ) * INTERVAL '1 minute'),
    resolution_due_at = t.created_at
        + (COALESCE(
            (SELECT p.resolution_target_minutes
             FROM   sla_policy_configs p
             WHERE  p.scope = 'PRIORITY'
               AND  p.priority = t.priority
               AND  p.is_active = TRUE
             ORDER  BY p.id
             LIMIT  1),
            (SELECT p.resolution_target_minutes
             FROM   sla_policy_configs p
             WHERE  p.scope = 'GLOBAL'
               AND  p.is_active = TRUE
             ORDER  BY p.id
             LIMIT  1)
          ) * INTERVAL '1 minute')
WHERE  t.first_response_due_at IS NULL
  AND  t.resolution_due_at IS NULL
  AND  t.status NOT IN ('RESOLVED', 'CLOSED')
  AND  COALESCE(
           (SELECT p.resolution_target_minutes
            FROM   sla_policy_configs p
            WHERE  p.scope = 'PRIORITY'
              AND  p.priority = t.priority
              AND  p.is_active = TRUE
            LIMIT  1),
           (SELECT p.resolution_target_minutes
            FROM   sla_policy_configs p
            WHERE  p.scope = 'GLOBAL'
              AND  p.is_active = TRUE
            LIMIT  1)
       ) IS NOT NULL;
