package com.caseflow.common.exception;

public class DuplicateCustomerCodeException extends RuntimeException {
    public DuplicateCustomerCodeException(String code) {
        super("Customer code already in use: " + code);
    }
}
