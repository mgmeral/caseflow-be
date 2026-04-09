package com.caseflow.customer.repository;

import com.caseflow.customer.domain.Customer;
import org.springframework.data.jpa.domain.Specification;

public final class CustomerSpecification {

    private CustomerSpecification() {}

    /** Filter by active/inactive status. Null = no filter. */
    public static Specification<Customer> hasStatus(Boolean isActive) {
        return (root, query, cb) ->
                isActive == null ? null : cb.equal(root.get("isActive"), isActive);
    }

    /** Case-insensitive search on name OR code. Null/blank = no filter. */
    public static Specification<Customer> nameOrCodeContains(String search) {
        return (root, query, cb) -> {
            if (search == null || search.isBlank()) return null;
            String like = "%" + search.toLowerCase() + "%";
            return cb.or(
                    cb.like(cb.lower(root.get("name")), like),
                    cb.like(cb.lower(root.get("code")), like)
            );
        };
    }
}
