package com.caseflow.customer.api;

import com.caseflow.customer.api.dto.CreateCustomerRequest;
import com.caseflow.customer.api.dto.CustomerResponse;
import com.caseflow.customer.api.dto.CustomerSummaryResponse;
import com.caseflow.customer.api.dto.UpdateCustomerRequest;
import com.caseflow.customer.api.mapper.CustomerMapper;
import com.caseflow.customer.service.CustomerService;
import com.caseflow.common.api.PagedResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Customers", description = "Customer account management")
@RestController
@RequestMapping("/api/customers")
public class CustomerController {

    private static final Logger log = LoggerFactory.getLogger(CustomerController.class);

    private final CustomerService customerService;
    private final CustomerMapper customerMapper;

    public CustomerController(CustomerService customerService, CustomerMapper customerMapper) {
        this.customerService = customerService;
        this.customerMapper = customerMapper;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PERM_CUSTOMER_MANAGE')")
    public ResponseEntity<CustomerResponse> createCustomer(@Valid @RequestBody CreateCustomerRequest request) {
        log.info("POST /customers — name: '{}', code: '{}'", request.name(), request.code());
        CustomerResponse response = customerMapper.toResponse(customerService.createCustomer(request.name(), request.code(), request.colorHex()));
        log.info("POST /customers succeeded — customerId: {}", response.id());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('PERM_CUSTOMER_MANAGE', 'PERM_TICKET_READ')")
    public ResponseEntity<CustomerResponse> getById(@PathVariable Long id) {
        log.info("GET /customers/{}", id);
        return ResponseEntity.ok(customerMapper.toResponse(customerService.getById(id)));
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('PERM_CUSTOMER_MANAGE', 'PERM_TICKET_READ')")
    public ResponseEntity<PagedResponse<CustomerSummaryResponse>> listCustomers(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean isActive,
            @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
        log.debug("GET /customers — search: '{}', isActive: {}, page: {}", search, isActive, pageable);
        return ResponseEntity.ok(PagedResponse.from(
                customerService.findFiltered(search, isActive, pageable)
                        .map(customerMapper::toSummaryResponse)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_CUSTOMER_MANAGE')")
    public ResponseEntity<CustomerResponse> updateCustomer(@PathVariable Long id,
                                                           @Valid @RequestBody UpdateCustomerRequest request) {
        log.info("PUT /customers/{}", id);
        CustomerResponse response = customerMapper.toResponse(customerService.updateCustomer(id, request.name(), request.code(), request.colorHex()));
        log.info("PUT /customers/{} succeeded", id);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}/activate")
    @PreAuthorize("hasAuthority('PERM_CUSTOMER_MANAGE')")
    public ResponseEntity<Void> activate(@PathVariable Long id) {
        log.info("PATCH /customers/{}/activate", id);
        customerService.activate(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/deactivate")
    @PreAuthorize("hasAuthority('PERM_CUSTOMER_MANAGE')")
    public ResponseEntity<Void> deactivate(@PathVariable Long id) {
        log.info("PATCH /customers/{}/deactivate", id);
        customerService.deactivate(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Permanently deletes a customer.
     *
     * <p>Returns 409 Conflict when tickets are still linked to this customer.
     * Callers must reassign or close all linked tickets before a delete is permitted.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_CUSTOMER_MANAGE')")
    public ResponseEntity<Void> deleteCustomer(@PathVariable Long id) {
        log.info("DELETE /customers/{}", id);
        customerService.deleteCustomer(id);
        log.info("DELETE /customers/{} succeeded", id);
        return ResponseEntity.noContent().build();
    }
}
