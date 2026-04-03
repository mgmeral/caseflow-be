package com.caseflow.common.exception;

public class MailTemplateNotFoundException extends RuntimeException {

    public MailTemplateNotFoundException(Long id) {
        super("Mail template not found: " + id);
    }

    public MailTemplateNotFoundException(String code) {
        super("Mail template not found for code: " + code);
    }
}
