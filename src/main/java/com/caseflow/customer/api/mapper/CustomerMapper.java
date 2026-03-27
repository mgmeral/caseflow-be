package com.caseflow.customer.api.mapper;

import com.caseflow.customer.api.dto.CreateCustomerRequest;
import com.caseflow.customer.api.dto.CustomerResponse;
import com.caseflow.customer.api.dto.CustomerSummaryResponse;
import com.caseflow.customer.api.dto.UpdateCustomerRequest;
import com.caseflow.customer.domain.Customer;
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
public interface CustomerMapper {

    // ── Entity → Response ─────────────────────────────────────────────────────

    CustomerResponse toResponse(Customer customer);

    CustomerSummaryResponse toSummaryResponse(Customer customer);

    // ── Request → Entity ──────────────────────────────────────────────────────

    /**
     * isActive is set to true by the service on creation.
     */
    @Mapping(target = "isActive", ignore = true)
    @Mapping(target = "contacts", ignore = true)
    Customer toEntity(CreateCustomerRequest request);

    /**
     * Applies mutable fields. isActive is managed via activate/deactivate service calls.
     */
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "isActive", ignore = true)
    @Mapping(target = "contacts", ignore = true)
    void updateEntity(UpdateCustomerRequest request, @MappingTarget Customer customer);
}
