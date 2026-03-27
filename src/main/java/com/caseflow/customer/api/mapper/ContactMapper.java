package com.caseflow.customer.api.mapper;

import com.caseflow.customer.api.dto.ContactResponse;
import com.caseflow.customer.api.dto.ContactSummaryResponse;
import com.caseflow.customer.api.dto.CreateContactRequest;
import com.caseflow.customer.api.dto.UpdateContactRequest;
import com.caseflow.customer.domain.Contact;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.ERROR,
        unmappedSourcePolicy = ReportingPolicy.IGNORE
)
public interface ContactMapper {

    // ── Entity → Response ─────────────────────────────────────────────────────

    @Mapping(target = "customerId", source = "customer.id")
    ContactResponse toResponse(Contact contact);

    @Mapping(target = "customerId", source = "customer.id")
    ContactSummaryResponse toSummaryResponse(Contact contact);

    // ── Request → Entity ──────────────────────────────────────────────────────

    /**
     * Maps only the fields that are not service-managed.
     * customerId is used by ContactService to load the Customer entity —
     * the service sets contact.setCustomer(...) before persisting.
     * isActive is initialized to true by the service.
     */
    @Mapping(target = "customer", ignore = true)
    @Mapping(target = "isActive", ignore = true)
    Contact toEntity(CreateContactRequest request);

    /**
     * Applies mutable fields. Email cannot be changed after creation.
     * customer and isActive are service-managed.
     */
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "customer", ignore = true)
    @Mapping(target = "email", ignore = true)
    void updateEntity(UpdateContactRequest request, @MappingTarget Contact contact);
}
