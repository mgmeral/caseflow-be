package com.caseflow.customer.api;

import com.caseflow.customer.api.dto.ContactResponse;
import com.caseflow.customer.api.dto.ContactSummaryResponse;
import com.caseflow.customer.api.dto.CreateContactRequest;
import com.caseflow.customer.api.dto.UpdateContactRequest;
import com.caseflow.customer.api.mapper.ContactMapper;
import com.caseflow.customer.service.ContactService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Contacts", description = "Customer contact management")
@RestController
@RequestMapping("/api/contacts")
public class ContactController {

    private final ContactService contactService;
    private final ContactMapper contactMapper;

    public ContactController(ContactService contactService, ContactMapper contactMapper) {
        this.contactService = contactService;
        this.contactMapper = contactMapper;
    }

    @PostMapping
    public ResponseEntity<ContactResponse> createContact(@Valid @RequestBody CreateContactRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                contactMapper.toResponse(contactService.createContact(
                        request.customerId(),
                        request.email(),
                        request.name(),
                        request.isPrimary()
                ))
        );
    }

    @GetMapping("/{id}")
    public ResponseEntity<ContactResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(contactMapper.toResponse(contactService.getById(id)));
    }

    @GetMapping
    public ResponseEntity<List<ContactSummaryResponse>> listContacts() {
        return ResponseEntity.ok(
                contactService.findAll().stream().map(contactMapper::toSummaryResponse).toList()
        );
    }

    @GetMapping("/by-email")
    public ResponseEntity<ContactResponse> getByEmail(@RequestParam String email) {
        return contactService.findByEmail(email)
                .map(contact -> ResponseEntity.ok(contactMapper.toResponse(contact)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/by-customer/{customerId}")
    public ResponseEntity<List<ContactSummaryResponse>> getByCustomer(@PathVariable Long customerId) {
        List<ContactSummaryResponse> contacts = contactService.findByCustomerId(customerId)
                .stream()
                .map(contactMapper::toSummaryResponse)
                .toList();
        return ResponseEntity.ok(contacts);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ContactResponse> updateContact(@PathVariable Long id,
                                                         @Valid @RequestBody UpdateContactRequest request) {
        return ResponseEntity.ok(
                contactMapper.toResponse(
                        contactService.updateContact(id, request.name(), request.isPrimary(), request.isActive())
                )
        );
    }
}
