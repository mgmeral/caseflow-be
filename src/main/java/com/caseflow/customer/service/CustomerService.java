package com.caseflow.customer.service;

import com.caseflow.common.exception.CustomerDeleteBlockedException;
import com.caseflow.common.exception.CustomerNotFoundException;
import com.caseflow.customer.domain.Customer;
import com.caseflow.customer.repository.CustomerEmailRoutingRuleRepository;
import com.caseflow.customer.repository.CustomerEmailSettingsRepository;
import com.caseflow.customer.repository.CustomerRepository;
import com.caseflow.ticket.repository.TicketRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CustomerService {

    private static final Logger log = LoggerFactory.getLogger(CustomerService.class);

    private final CustomerRepository customerRepository;
    private final TicketRepository ticketRepository;
    private final CustomerEmailSettingsRepository settingsRepository;
    private final CustomerEmailRoutingRuleRepository ruleRepository;

    public CustomerService(CustomerRepository customerRepository,
                           TicketRepository ticketRepository,
                           CustomerEmailSettingsRepository settingsRepository,
                           CustomerEmailRoutingRuleRepository ruleRepository) {
        this.customerRepository = customerRepository;
        this.ticketRepository = ticketRepository;
        this.settingsRepository = settingsRepository;
        this.ruleRepository = ruleRepository;
    }

    @Transactional
    public Customer createCustomer(String name, String code) {
        log.info("Creating customer — name: '{}', code: '{}'", name, code);
        Customer customer = new Customer();
        customer.setName(name);
        customer.setCode(code);
        customer.setIsActive(true);
        Customer saved = customerRepository.save(customer);
        log.info("Customer created — customerId: {}", saved.getId());
        return saved;
    }

    @Transactional
    public Customer updateCustomer(Long customerId, String name, String code) {
        log.info("Updating customer {} — name: '{}', code: '{}'", customerId, name, code);
        Customer customer = findOrThrow(customerId);
        customer.setName(name);
        customer.setCode(code);
        Customer saved = customerRepository.save(customer);
        log.info("Customer {} updated", customerId);
        return saved;
    }

    @Transactional
    public void activate(Long customerId) {
        log.info("Activating customer {}", customerId);
        Customer customer = findOrThrow(customerId);
        customer.setIsActive(true);
        customerRepository.save(customer);
        log.info("Customer {} activated", customerId);
    }

    @Transactional
    public void deactivate(Long customerId) {
        log.info("Deactivating customer {}", customerId);
        Customer customer = findOrThrow(customerId);
        customer.setIsActive(false);
        customerRepository.save(customer);
        log.info("Customer {} deactivated", customerId);
    }

    @Transactional(readOnly = true)
    public Customer getById(Long customerId) {
        return findOrThrow(customerId);
    }

    @Transactional(readOnly = true)
    public List<Customer> findAll() {
        return customerRepository.findAll();
    }

    /**
     * Deletes a customer and all associated email configuration (settings + routing rules).
     *
     * <p><b>Business rule</b>: delete is blocked when any ticket is linked to this customer.
     * Callers receive {@link CustomerDeleteBlockedException} (mapped to 409 Conflict) in that case.
     * Contacts are cascade-deleted by the JPA relationship on {@code Customer}.
     */
    @Transactional
    public void deleteCustomer(Long customerId) {
        Customer customer = findOrThrow(customerId);
        long ticketCount = ticketRepository.findByCustomerId(customerId).size();
        if (ticketCount > 0) {
            log.warn("Delete blocked — customerId: {} has {} linked ticket(s)", customerId, ticketCount);
            throw new CustomerDeleteBlockedException(customerId, ticketCount);
        }
        ruleRepository.deleteByCustomerId(customerId);
        settingsRepository.findByCustomerId(customerId).ifPresent(settingsRepository::delete);
        customerRepository.delete(customer);
        log.info("Customer {} deleted", customerId);
    }

    private Customer findOrThrow(Long customerId) {
        return customerRepository.findById(customerId)
                .orElseThrow(() -> new CustomerNotFoundException(customerId));
    }
}
