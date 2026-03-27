package com.caseflow.customer.service;

import com.caseflow.common.exception.ContactNotFoundException;
import com.caseflow.common.exception.CustomerNotFoundException;
import com.caseflow.customer.domain.Contact;
import com.caseflow.customer.domain.Customer;
import com.caseflow.customer.repository.ContactRepository;
import com.caseflow.customer.repository.CustomerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class ContactService {

    private final ContactRepository contactRepository;
    private final CustomerRepository customerRepository;

    public ContactService(ContactRepository contactRepository, CustomerRepository customerRepository) {
        this.contactRepository = contactRepository;
        this.customerRepository = customerRepository;
    }

    @Transactional
    public Contact createContact(Long customerId, String email, String name, boolean isPrimary) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new CustomerNotFoundException(customerId));
        if (isPrimary) {
            clearPrimary(customerId);
        }
        Contact contact = new Contact();
        contact.setCustomer(customer);
        contact.setEmail(email);
        contact.setName(name);
        contact.setIsPrimary(isPrimary);
        contact.setIsActive(true);
        return contactRepository.save(contact);
    }

    @Transactional
    public Contact updateContact(Long contactId, String name, boolean isPrimary, boolean isActive) {
        Contact contact = findOrThrow(contactId);
        if (isPrimary && !contact.getIsPrimary()) {
            clearPrimary(contact.getCustomer().getId());
        }
        contact.setName(name);
        contact.setIsPrimary(isPrimary);
        contact.setIsActive(isActive);
        return contactRepository.save(contact);
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
