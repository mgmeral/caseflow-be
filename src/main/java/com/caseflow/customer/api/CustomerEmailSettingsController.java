package com.caseflow.customer.api;

import com.caseflow.customer.api.dto.CustomerEmailSettingsRequest;
import com.caseflow.customer.api.dto.CustomerEmailSettingsResponse;
import com.caseflow.customer.api.dto.RoutingRuleRequest;
import com.caseflow.customer.api.dto.RoutingRuleResponse;
import com.caseflow.customer.api.mapper.CustomerEmailSettingsMapper;
import com.caseflow.customer.domain.CustomerEmailSettings;
import com.caseflow.customer.service.CustomerEmailSettingsService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.util.List;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Customer Email Settings", description = "Email routing settings per customer")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/customers/{customerId}/email-settings")
public class CustomerEmailSettingsController {

    private static final Logger log = LoggerFactory.getLogger(CustomerEmailSettingsController.class);

    private final CustomerEmailSettingsService settingsService;
    private final CustomerEmailSettingsMapper mapper;

    public CustomerEmailSettingsController(CustomerEmailSettingsService settingsService,
                                           CustomerEmailSettingsMapper mapper) {
        this.settingsService = settingsService;
        this.mapper = mapper;
    }

    @PutMapping
    @PreAuthorize("hasAuthority('PERM_EMAIL_CONFIG_MANAGE')")
    public ResponseEntity<CustomerEmailSettingsResponse> upsert(
            @PathVariable Long customerId,
            @Valid @RequestBody CustomerEmailSettingsRequest request) {
        log.info("PUT /customers/{}/email-settings", customerId);
        CustomerEmailSettings settings = settingsService.upsert(customerId, mapper.toEntity(request));
        return ResponseEntity.ok(mapper.toResponse(settings, settingsService.findAllRules(customerId)));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PERM_EMAIL_CONFIG_VIEW')")
    public ResponseEntity<CustomerEmailSettingsResponse> get(@PathVariable Long customerId) {
        return settingsService.findByCustomerId(customerId)
                .map(s -> ResponseEntity.ok(mapper.toResponse(s, settingsService.findAllRules(customerId))))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Standalone routing-rules list — always returns the complete list (empty array when none exist).
     * Safe to call even when no email-settings record has been created yet for this customer.
     */
    @GetMapping("/rules")
    @PreAuthorize("hasAuthority('PERM_EMAIL_CONFIG_VIEW')")
    public ResponseEntity<List<RoutingRuleResponse>> listRules(@PathVariable Long customerId) {
        log.info("GET /customers/{}/email-settings/rules", customerId);
        List<RoutingRuleResponse> rules = settingsService.findAllRules(customerId)
                .stream().map(mapper::toRuleResponse).toList();
        return ResponseEntity.ok(rules);
    }

    @PostMapping("/rules")
    @PreAuthorize("hasAuthority('PERM_EMAIL_CONFIG_MANAGE')")
    public ResponseEntity<RoutingRuleResponse> addRule(@PathVariable Long customerId,
                                                        @Valid @RequestBody RoutingRuleRequest request) {
        log.info("POST /customers/{}/email-settings/rules — type: {}, value: '{}'",
                customerId, request.senderMatchType(), request.matchValue());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(mapper.toRuleResponse(settingsService.addRule(customerId, mapper.toRuleEntity(request))));
    }

    @PutMapping("/rules/{ruleId}")
    @PreAuthorize("hasAuthority('PERM_EMAIL_CONFIG_MANAGE')")
    public ResponseEntity<RoutingRuleResponse> updateRule(@PathVariable Long customerId,
                                                           @PathVariable Long ruleId,
                                                           @Valid @RequestBody RoutingRuleRequest request) {
        log.info("PUT /customers/{}/email-settings/rules/{}", customerId, ruleId);
        return ResponseEntity.ok(
                mapper.toRuleResponse(settingsService.updateRule(customerId, ruleId, mapper.toRuleEntity(request))));
    }

    @DeleteMapping("/rules/{ruleId}")
    @PreAuthorize("hasAuthority('PERM_EMAIL_CONFIG_MANAGE')")
    public ResponseEntity<Void> deleteRule(@PathVariable Long customerId, @PathVariable Long ruleId) {
        log.info("DELETE /customers/{}/email-settings/rules/{}", customerId, ruleId);
        settingsService.deleteRule(ruleId);
        return ResponseEntity.noContent().build();
    }
}
