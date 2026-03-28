package com.caseflow.customer.repository;

import com.caseflow.customer.domain.CustomerEmailRoutingRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CustomerEmailRoutingRuleRepository extends JpaRepository<CustomerEmailRoutingRule, Long> {

    List<CustomerEmailRoutingRule> findByCustomerIdAndIsActiveTrueOrderByPriorityAsc(Long customerId);

    List<CustomerEmailRoutingRule> findByCustomerId(Long customerId);
}
