package com.caseflow.email.service;

import com.caseflow.email.document.EmailDocument;

public interface EmailProcessingService {

    EmailDocument process(ParsedEmail parsedEmail);
}
