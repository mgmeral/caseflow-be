package com.caseflow.customer.service;

import com.caseflow.common.exception.CustomerNotFoundException;
import com.caseflow.customer.domain.Customer;
import com.caseflow.customer.repository.CustomerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CustomerService {

    private final CustomerRepository customerRepository;

    public CustomerService(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    @Transactional
    public Customer createCustomer(String name, String code) {
        Customer customer = new Customer();
        customer.setName(name);
        customer.setCode(code);
        customer.setIsActive(true);
        return customerRepository.save(customer);
    }

    @Transactional
    public Customer updateCustomer(Long customerId, String name, String code) {
        Customer customer = findOrThrow(customerId);
        customer.setName(name);
        customer.setCode(code);
        return customerRepository.save(customer);
    }

    @Transactional
    public void activate(Long customerId) {
        Customer customer = findOrThrow(customerId);
        customer.setIsActive(true);
        customerRepository.save(customer);
    }

    @Transactional
    public void deactivate(Long customerId) {
        Customer customer = findOrThrow(customerId);
        customer.setIsActive(false);
        customerRepository.save(customer);
    }

    @Transactional(readOnly = true)
    public Customer getById(Long customerId) {
        return findOrThrow(customerId);
    }

    @Transactional(readOnly = true)
    public List<Customer> findAll() {
        return customerRepository.findAll();
    }

    private Customer findOrThrow(Long customerId) {
        return customerRepository.findById(customerId)
                .orElseThrow(() -> new CustomerNotFoundException(customerId));
    }
}
