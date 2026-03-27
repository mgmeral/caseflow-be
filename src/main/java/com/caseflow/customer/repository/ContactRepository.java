package com.caseflow.customer.repository;

import com.caseflow.customer.domain.Contact;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ContactRepository extends JpaRepository<Contact, Long> {

    Optional<Contact> findByEmail(String email);

    List<Contact> findByCustomer_Id(Long customerId);

    Optional<Contact> findByCustomer_IdAndIsPrimaryTrue(Long customerId);

    boolean existsByEmail(String email);
}
