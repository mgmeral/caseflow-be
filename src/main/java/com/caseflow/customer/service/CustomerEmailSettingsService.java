package com.caseflow.customer.service;

import com.caseflow.common.exception.RoutingRuleNotFoundException;
import com.caseflow.customer.domain.CustomerEmailRoutingRule;
import com.caseflow.customer.domain.CustomerEmailSettings;
import com.caseflow.customer.repository.CustomerEmailRoutingRuleRepository;
import com.caseflow.customer.repository.CustomerEmailSettingsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class CustomerEmailSettingsService {

    private static final Logger log = LoggerFactory.getLogger(CustomerEmailSettingsService.class);

    private final CustomerEmailSettingsRepository settingsRepository;
    private final CustomerEmailRoutingRuleRepository ruleRepository;

    public CustomerEmailSettingsService(CustomerEmailSettingsRepository settingsRepository,
                                        CustomerEmailRoutingRuleRepository ruleRepository) {
        this.settingsRepository = settingsRepository;
        this.ruleRepository = ruleRepository;
    }

    @Transactional
    public CustomerEmailSettings upsert(Long customerId, CustomerEmailSettings settings) {
        CustomerEmailSettings existing = settingsRepository.findByCustomerId(customerId)
                .orElseGet(() -> {
                    CustomerEmailSettings s = new CustomerEmailSettings();
                    s.setCustomerId(customerId);
                    return s;
                });
        existing.setUnknownSenderPolicy(settings.getUnknownSenderPolicy());
        existing.setMatchingStrategy(settings.getMatchingStrategy());
        existing.setIsActive(settings.getIsActive());
        existing.setTrustedContactsOnly(settings.getTrustedContactsOnly());
        existing.setAutoCreateContact(settings.getAutoCreateContact());
        existing.setAllowSubdomains(settings.getAllowSubdomains());
        existing.setDefaultGroupId(settings.getDefaultGroupId());
        existing.setDefaultPriority(settings.getDefaultPriority());
        CustomerEmailSettings saved = settingsRepository.save(existing);
        log.info("CustomerEmailSettings upserted — customerId: {}", customerId);
        return saved;
    }

    @Transactional(readOnly = true)
    public Optional<CustomerEmailSettings> findByCustomerId(Long customerId) {
        return settingsRepository.findByCustomerId(customerId);
    }

    @Transactional
    public CustomerEmailRoutingRule addRule(Long customerId, CustomerEmailRoutingRule rule) {
        rule.setCustomerId(customerId);
        CustomerEmailRoutingRule saved = ruleRepository.save(rule);
        log.info("Routing rule added — customerId: {}, matchType: {}, value: '{}'",
                customerId, rule.getSenderMatchType(), rule.getMatchValue());
        return saved;
    }

    @Transactional
    public CustomerEmailRoutingRule updateRule(Long customerId, Long ruleId, CustomerEmailRoutingRule updates) {
        CustomerEmailRoutingRule existing = ruleRepository.findByIdAndCustomerId(ruleId, customerId)
                .orElseThrow(() -> new RoutingRuleNotFoundException(ruleId, customerId));
        existing.setSenderMatchType(updates.getSenderMatchType());
        existing.setMatchValue(updates.getMatchValue());
        existing.setPriority(updates.getPriority());
        existing.setIsActive(updates.getIsActive());
        CustomerEmailRoutingRule saved = ruleRepository.save(existing);
        log.info("Routing rule updated — id: {}, customerId: {}", ruleId, customerId);
        return saved;
    }

    @Transactional
    public void deleteRule(Long ruleId) {
        ruleRepository.deleteById(ruleId);
        log.info("Routing rule deleted — id: {}", ruleId);
    }

    @Transactional(readOnly = true)
    public List<CustomerEmailRoutingRule> findActiveRules(Long customerId) {
        return ruleRepository.findByCustomerIdAndIsActiveTrueOrderByPriorityAsc(customerId);
    }

    @Transactional(readOnly = true)
    public List<CustomerEmailRoutingRule> findAllRules(Long customerId) {
        return ruleRepository.findByCustomerId(customerId);
    }
}
