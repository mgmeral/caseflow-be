package com.caseflow.customer.repository;

import com.caseflow.customer.domain.CustomerEmailSettings;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CustomerEmailSettingsRepository extends JpaRepository<CustomerEmailSettings, Long> {

    Optional<CustomerEmailSettings> findByCustomerId(Long customerId);
}
