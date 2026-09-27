package com.caseflow.email.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class PiiMaskerTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "ali.veli@acme.com                       | a***@acme.com",
            "Ali Veli <ali.veli@mail.acme.com.tr>    | Ali Veli <a***@mail.acme.com.tr>",
            "Unknown sender: x@y.io                  | Unknown sender: x***@y.io",
            "Order 12345678 please                   | Order *** please",
            "Call me on +90 532 123 45 67            | Call me on ***",
            "Call me on 0532-123-4567                | Call me on ***",
            "IBAN TR33 0006 1005 1978 6457 8413 26   | IBAN ***",
            "Re: [TKT-0000123] refund                | Re: [TKT-0000123] refund",
            "Meeting on 2026-09-27 at 10:30          | Meeting on 2026-09-27 at 10:30",
            "Invoice INV2026001 attached             | Invoice INV2026001 attached",
    })
    void masksPersonalDataOnly(String input, String expected) {
        assertThat(PiiMasker.mask(input)).isEqualTo(expected);
    }

    @Test
    void passesNullAndEmptyThrough() {
        assertThat(PiiMasker.mask(null)).isNull();
        assertThat(PiiMasker.mask("")).isEmpty();
    }
}
