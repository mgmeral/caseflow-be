package com.caseflow.customer.repository;

import com.caseflow.customer.domain.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    Optional<Customer> findByCode(String code);

    boolean existsByCode(String code);
}
