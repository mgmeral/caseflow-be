package com.caseflow.email.domain;

/** Authentication mechanism used when connecting to the IMAP server. */
public enum AuthType {
    /** Plain username + password (or app-password) authentication. */
    PASSWORD,
    /** OAuth2 / XOAUTH2 — client-credentials flow (app-only, no user interaction). */
    OAUTH2
}
