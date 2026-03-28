package com.caseflow.customer.service;

import com.caseflow.common.exception.ContactNotFoundException;
import com.caseflow.common.exception.CustomerNotFoundException;
import com.caseflow.common.exception.DuplicateEmailException;
import com.caseflow.customer.domain.Contact;
import com.caseflow.customer.domain.Customer;
import com.caseflow.customer.repository.ContactRepository;
import com.caseflow.customer.repository.CustomerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class ContactService {

    private static final Logger log = LoggerFactory.getLogger(ContactService.class);

    private final ContactRepository contactRepository;
    private final CustomerRepository customerRepository;

    public ContactService(ContactRepository contactRepository, CustomerRepository customerRepository) {
        this.contactRepository = contactRepository;
        this.customerRepository = customerRepository;
    }

    @Transactional
    public Contact createContact(Long customerId, String email, String name, boolean isPrimary) {
        log.info("Creating contact for customerId: {}, isPrimary: {}", customerId, isPrimary);
        if (contactRepository.existsByEmail(email)) {
            log.warn("Contact creation rejected — email already in use: {}", email);
            throw new DuplicateEmailException(email);
        }
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new CustomerNotFoundException(customerId));
        if (isPrimary) {
            log.info("Clearing existing primary contact for customerId: {} before setting new primary", customerId);
            clearPrimary(customerId);
        }
        Contact contact = new Contact();
        contact.setCustomer(customer);
        contact.setEmail(email);
        contact.setName(name);
        contact.setIsPrimary(isPrimary);
        contact.setIsActive(true);
        Contact saved = contactRepository.save(contact);
        log.info("Contact created — contactId: {}, customerId: {}", saved.getId(), customerId);
        return saved;
    }

    @Transactional
    public Contact updateContact(Long contactId, String name, boolean isPrimary, boolean isActive) {
        log.info("Updating contact {} — isPrimary: {}, isActive: {}", contactId, isPrimary, isActive);
        Contact contact = findOrThrow(contactId);
        if (isPrimary && !contact.getIsPrimary()) {
            log.info("Clearing existing primary contact for customerId: {} before promoting contact {}",
                    contact.getCustomer().getId(), contactId);
            clearPrimary(contact.getCustomer().getId());
        }
        contact.setName(name);
        contact.setIsPrimary(isPrimary);
        contact.setIsActive(isActive);
        Contact saved = contactRepository.save(contact);
        log.info("Contact {} updated", contactId);
        return saved;
    }

    @Transactional(readOnly = true)
    public Optional<Contact> findByEmail(String email) {
        return contactRepository.findByEmail(email);
    }

    @Transactional(readOnly = true)
    public Contact getById(Long contactId) {
        return findOrThrow(contactId);
    }

    @Transactional(readOnly = true)
    public List<Contact> findAll() {
        return contactRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<Contact> findByCustomerId(Long customerId) {
        return contactRepository.findByCustomer_Id(customerId);
    }

    private void clearPrimary(Long customerId) {
        contactRepository.findByCustomer_IdAndIsPrimaryTrue(customerId).ifPresent(existing -> {
            existing.setIsPrimary(false);
            contactRepository.save(existing);
        });
    }

    private Contact findOrThrow(Long contactId) {
        return contactRepository.findById(contactId)
                .orElseThrow(() -> new ContactNotFoundException(contactId));
    }
}
