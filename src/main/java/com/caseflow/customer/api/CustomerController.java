package com.caseflow.customer.api;

import com.caseflow.customer.api.dto.CreateCustomerRequest;
import com.caseflow.customer.api.dto.CustomerResponse;
import com.caseflow.customer.api.dto.CustomerSummaryResponse;
import com.caseflow.customer.api.dto.UpdateCustomerRequest;
import com.caseflow.customer.api.mapper.CustomerMapper;
import com.caseflow.customer.service.CustomerService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Customers", description = "Customer account management")
@RestController
@RequestMapping("/api/customers")
public class CustomerController {

    private final CustomerService customerService;
    private final CustomerMapper customerMapper;

    public CustomerController(CustomerService customerService, CustomerMapper customerMapper) {
        this.customerService = customerService;
        this.customerMapper = customerMapper;
    }

    @PostMapping
    public ResponseEntity<CustomerResponse> createCustomer(@Valid @RequestBody CreateCustomerRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(customerMapper.toResponse(customerService.createCustomer(request.name(), request.code())));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CustomerResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(customerMapper.toResponse(customerService.getById(id)));
    }

    @GetMapping
    public ResponseEntity<List<CustomerSummaryResponse>> listCustomers() {
        return ResponseEntity.ok(
                customerService.findAll().stream().map(customerMapper::toSummaryResponse).toList()
        );
    }

    @PutMapping("/{id}")
    public ResponseEntity<CustomerResponse> updateCustomer(@PathVariable Long id,
                                                           @Valid @RequestBody UpdateCustomerRequest request) {
        return ResponseEntity.ok(
                customerMapper.toResponse(customerService.updateCustomer(id, request.name(), request.code()))
        );
    }

    @PatchMapping("/{id}/activate")
    public ResponseEntity<Void> activate(@PathVariable Long id) {
        customerService.activate(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/deactivate")
    public ResponseEntity<Void> deactivate(@PathVariable Long id) {
        customerService.deactivate(id);
        return ResponseEntity.noContent().build();
    }
}
