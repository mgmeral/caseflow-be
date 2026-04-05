package com.caseflow.storage;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AttachmentKeyStrategyTest {

    private final AttachmentKeyStrategy strategy = new AttachmentKeyStrategy();

    // ── stagingKey ────────────────────────────────────────────────────────────

    @Test
    void stagingKey_containsMailboxIdAndImapUid() {
        String key = strategy.stagingKey(7L, 12345L, "report.pdf");

        assertThat(key).startsWith("staging/mailboxes/7/precheck/12345/attachments/");
    }

    @Test
    void stagingKey_containsSanitizedFileName() {
        String key = strategy.stagingKey(1L, 100L, "my file (v2).pdf");

        assertThat(key).contains("my_file__v2_.pdf");
    }

    @Test
    void stagingKey_neverContainsUnknown() {
        String key = strategy.stagingKey(1L, 42L, "attachment.txt");

        assertThat(key).doesNotContain("unknown");
    }

    @Test
    void stagingKey_usesUidPrefix() {
        String key = strategy.stagingKey(3L, 9999L, "file.txt");

        assertThat(key).contains("/precheck/9999/");
    }

    @Test
    void stagingKey_includesUuidComponent() {
        String key1 = strategy.stagingKey(1L, 1L, "file.txt");
        String key2 = strategy.stagingKey(1L, 1L, "file.txt");

        // Two calls produce distinct keys (UUID randomness)
        assertThat(key1).isNotEqualTo(key2);
    }

    @Test
    void stagingKey_handlesNullFileName() {
        String key = strategy.stagingKey(1L, 1L, null);

        assertThat(key).contains("unnamed");
    }

    // ── finalKey ─────────────────────────────────────────────────────────────

    @Test
    void finalKey_containsTicketPublicIdAndEmailDocumentId() {
        UUID publicId = UUID.randomUUID();
        String key = strategy.finalKey(publicId, "doc-abc123", 99L, "invoice.pdf");

        assertThat(key).startsWith("tickets/" + publicId + "/emails/doc-abc123/attachments/");
    }

    @Test
    void finalKey_isDeterministicForSameInputs() {
        UUID publicId = UUID.randomUUID();
        String key1 = strategy.finalKey(publicId, "doc-1", 42L, "file.txt");
        String key2 = strategy.finalKey(publicId, "doc-1", 42L, "file.txt");

        assertThat(key1).isEqualTo(key2);
    }

    @Test
    void finalKey_includesAttachmentId() {
        UUID publicId = UUID.randomUUID();
        String key = strategy.finalKey(publicId, "doc-1", 42L, "file.txt");

        assertThat(key).contains("/attachments/42/");
    }

    @Test
    void finalKey_neverContainsUnknown() {
        UUID publicId = UUID.randomUUID();
        String key = strategy.finalKey(publicId, "emailDoc-42", 7L, "report.xlsx");

        assertThat(key).doesNotContain("unknown");
    }

    // ── directUploadKey ───────────────────────────────────────────────────────

    @Test
    void directUploadKey_containsTicketPublicId() {
        UUID publicId = UUID.randomUUID();
        String key = strategy.directUploadKey(publicId, "contract.pdf");

        assertThat(key).startsWith("tickets/" + publicId + "/attachments/");
    }

    @Test
    void directUploadKey_containsSanitizedFileName() {
        UUID publicId = UUID.randomUUID();
        String key = strategy.directUploadKey(publicId, "my file (v2).pdf");

        assertThat(key).contains("my_file__v2_.pdf");
    }

    @Test
    void directUploadKey_neverContainsUnknown() {
        UUID publicId = UUID.randomUUID();
        String key = strategy.directUploadKey(publicId, "attachment.txt");

        assertThat(key).doesNotContain("unknown");
    }

    @Test
    void directUploadKey_doesNotContainEmailsSegment() {
        UUID publicId = UUID.randomUUID();
        String key = strategy.directUploadKey(publicId, "file.txt");

        // Direct uploads have no email document — key must not include /emails/
        assertThat(key).doesNotContain("/emails/");
    }

    @Test
    void directUploadKey_isUnique() {
        UUID publicId = UUID.randomUUID();
        String key1 = strategy.directUploadKey(publicId, "file.txt");
        String key2 = strategy.directUploadKey(publicId, "file.txt");

        assertThat(key1).isNotEqualTo(key2);
    }
}
