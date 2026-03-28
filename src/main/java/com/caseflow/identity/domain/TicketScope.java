package com.caseflow.identity.domain;

/**
 * Defines the ticket visibility scope granted to a role.
 * ALL               — see every ticket in the system
 * OWN_GROUPS        — see tickets assigned to any of the user's groups
 * OWN_AND_OWN_GROUPS — see tickets assigned to self OR own groups
 * ASSIGNED_ONLY     — see only tickets directly assigned to the user
 */
public enum TicketScope {
    ALL,
    OWN_GROUPS,
    OWN_AND_OWN_GROUPS,
    ASSIGNED_ONLY
}
