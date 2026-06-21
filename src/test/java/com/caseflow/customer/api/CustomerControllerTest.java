package com.caseflow.customer.api;

import com.caseflow.auth.CaseFlowUserDetailsService;
import com.caseflow.auth.JwtTokenService;
import com.caseflow.common.exception.CustomerDeleteBlockedException;
import com.caseflow.common.exception.CustomerNotFoundException;
import com.caseflow.common.security.SecurityConfig;
import com.caseflow.customer.api.dto.CreateCustomerRequest;
import com.caseflow.customer.api.dto.CustomerResponse;
import com.caseflow.customer.api.dto.CustomerSummaryResponse;
import com.caseflow.customer.api.dto.UpdateCustomerRequest;
import com.caseflow.customer.api.mapper.CustomerMapper;
import com.caseflow.customer.domain.Customer;
import com.caseflow.customer.service.CustomerService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CustomerController.class)
@Import(SecurityConfig.class)
class CustomerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private JwtTokenService jwtTokenService;

    @MockBean
    private CaseFlowUserDetailsService userDetailsService;

    @MockBean
    private CustomerService customerService;

    @MockBean
    private CustomerMapper customerMapper;

    // ── GET /api/customers/{id} ───────────────────────────────────────────────

    @Test
    @WithMockUser(authorities = "PERM_TICKET_READ")
    void getById_returns200_whenFound() throws Exception {
        Customer c = makeCustomer(1L, "ACME", "#3B82F6");
        CustomerResponse response = new CustomerResponse(1L, "ACME Corp", "ACME", true, "#3B82F6", Instant.now(), Instant.now());

        when(customerService.getById(1L)).thenReturn(c);
        when(customerMapper.toResponse(c)).thenReturn(response);

        mockMvc.perform(get("/api/customers/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("ACME Corp"))
                .andExpect(jsonPath("$.colorHex").value("#3B82F6"));
    }

    @Test
    @WithMockUser(authorities = "PERM_TICKET_READ")
    void getById_returns404_whenNotFound() throws Exception {
        when(customerService.getById(99L)).thenThrow(new CustomerNotFoundException(99L));

        mockMvc.perform(get("/api/customers/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    // ── GET /api/customers ────────────────────────────────────────────────────

    @Test
    @WithMockUser(authorities = "PERM_TICKET_READ")
    void listCustomers_summaryIncludesIsActiveAndColorHex() throws Exception {
        Customer c = makeCustomer(1L, "ACME", "#3B82F6");
        CustomerSummaryResponse summary = new CustomerSummaryResponse(1L, "ACME Corp", "ACME", true, "#3B82F6");

        when(customerService.findAll()).thenReturn(List.of(c));
        when(customerMapper.toSummaryResponse(c)).thenReturn(summary);

        mockMvc.perform(get("/api/customers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("ACME"))
                .andExpect(jsonPath("$[0].isActive").value(true))
                .andExpect(jsonPath("$[0].colorHex").value("#3B82F6"));
    }

    @Test
    @WithMockUser(authorities = "PERM_TICKET_READ")
    void listCustomers_isActiveFilter_delegatesToService() throws Exception {
        Customer c = makeCustomer(2L, "BETA", null);
        CustomerSummaryResponse summary = new CustomerSummaryResponse(2L, "Beta Ltd", "BETA", false, null);

        when(customerService.findFiltered(isNull(), eq(false))).thenReturn(List.of(c));
        when(customerMapper.toSummaryResponse(c)).thenReturn(summary);

        mockMvc.perform(get("/api/customers").param("isActive", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].isActive").value(false));
    }

    @Test
    @WithMockUser(authorities = "PERM_TICKET_READ")
    void listCustomers_searchFilter_delegatesToService() throws Exception {
        Customer c = makeCustomer(1L, "ACME", null);
        CustomerSummaryResponse summary = new CustomerSummaryResponse(1L, "ACME Corp", "ACME", true, null);

        when(customerService.findFiltered(eq("ACM"), isNull())).thenReturn(List.of(c));
        when(customerMapper.toSummaryResponse(c)).thenReturn(summary);

        mockMvc.perform(get("/api/customers").param("search", "ACM"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("ACME Corp"));
    }

    @Test
    void listCustomers_returns401_whenUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/customers"))
                .andExpect(status().isUnauthorized());
    }

    // ── POST /api/customers ───────────────────────────────────────────────────

    @Test
    @WithMockUser(authorities = "PERM_CUSTOMER_MANAGE")
    void createCustomer_returns201_withColorHex() throws Exception {
        CreateCustomerRequest request = new CreateCustomerRequest("ACME Corp", "ACME", "#3B82F6");
        Customer customer = makeCustomer(1L, "ACME", "#3B82F6");
        CustomerResponse response = new CustomerResponse(1L, "ACME Corp", "ACME", true, "#3B82F6", Instant.now(), Instant.now());

        when(customerService.createCustomer("ACME Corp", "ACME", "#3B82F6")).thenReturn(customer);
        when(customerMapper.toResponse(customer)).thenReturn(response);

        mockMvc.perform(post("/api/customers")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.colorHex").value("#3B82F6"));
    }

    @Test
    @WithMockUser(authorities = "PERM_CUSTOMER_MANAGE")
    void createCustomer_returns201_withoutColorHex() throws Exception {
        CreateCustomerRequest request = new CreateCustomerRequest("ACME Corp", "ACME", null);
        Customer customer = makeCustomer(1L, "ACME", null);
        CustomerResponse response = new CustomerResponse(1L, "ACME Corp", "ACME", true, null, Instant.now(), Instant.now());

        when(customerService.createCustomer("ACME Corp", "ACME", null)).thenReturn(customer);
        when(customerMapper.toResponse(customer)).thenReturn(response);

        mockMvc.perform(post("/api/customers")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.colorHex").isEmpty());
    }

    @Test
    @WithMockUser(authorities = "PERM_CUSTOMER_MANAGE")
    void createCustomer_returns400_onInvalidColorHex() throws Exception {
        // Invalid: no '#' prefix
        String body = """
                {"name":"ACME Corp","code":"ACME","colorHex":"3B82F6"}
                """;

        mockMvc.perform(post("/api/customers")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(authorities = "PERM_CUSTOMER_MANAGE")
    void createCustomer_returns400_onInvalidColorHex_wrongLength() throws Exception {
        String body = """
                {"name":"ACME Corp","code":"ACME","colorHex":"#3B82"}
                """;

        mockMvc.perform(post("/api/customers")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    // ── PUT /api/customers/{id} ───────────────────────────────────────────────

    @Test
    @WithMockUser(authorities = "PERM_CUSTOMER_MANAGE")
    void updateCustomer_returns200_withColorHex() throws Exception {
        UpdateCustomerRequest request = new UpdateCustomerRequest("New Name", "NEWCODE", "#FF5733");
        Customer customer = makeCustomer(1L, "NEWCODE", "#FF5733");
        CustomerResponse response = new CustomerResponse(1L, "New Name", "NEWCODE", true, "#FF5733", Instant.now(), Instant.now());

        when(customerService.updateCustomer(1L, "New Name", "NEWCODE", "#FF5733")).thenReturn(customer);
        when(customerMapper.toResponse(customer)).thenReturn(response);

        mockMvc.perform(put("/api/customers/1")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.colorHex").value("#FF5733"));
    }

    @Test
    @WithMockUser(authorities = "PERM_CUSTOMER_MANAGE")
    void updateCustomer_returns200_withoutColorHex() throws Exception {
        UpdateCustomerRequest request = new UpdateCustomerRequest("New Name", "NEWCODE", null);
        Customer customer = makeCustomer(1L, "NEWCODE", "#3B82F6");
        CustomerResponse response = new CustomerResponse(1L, "New Name", "NEWCODE", true, "#3B82F6", Instant.now(), Instant.now());

        when(customerService.updateCustomer(1L, "New Name", "NEWCODE", null)).thenReturn(customer);
        when(customerMapper.toResponse(customer)).thenReturn(response);

        mockMvc.perform(put("/api/customers/1")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                // existing color is preserved (null colorHex in request = no-op in service)
                .andExpect(jsonPath("$.colorHex").value("#3B82F6"));
    }

    @Test
    @WithMockUser(authorities = "PERM_CUSTOMER_MANAGE")
    void updateCustomer_returns400_onInvalidColorHex() throws Exception {
        String body = """
                {"name":"New Name","code":"NEWCODE","colorHex":"not-a-color"}
                """;

        mockMvc.perform(put("/api/customers/1")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    // ── DELETE /api/customers/{id} ────────────────────────────────────────────

    @Test
    @WithMockUser(authorities = "PERM_CUSTOMER_MANAGE")
    void deleteCustomer_returns204_whenNoTickets() throws Exception {
        doNothing().when(customerService).deleteCustomer(1L);

        mockMvc.perform(delete("/api/customers/1").with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(authorities = "PERM_CUSTOMER_MANAGE")
    void deleteCustomer_returns409_whenTicketsExist() throws Exception {
        doThrow(new CustomerDeleteBlockedException(1L, 3L))
                .when(customerService).deleteCustomer(1L);

        mockMvc.perform(delete("/api/customers/1").with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CUSTOMER_DELETE_BLOCKED"));
    }

    @Test
    @WithMockUser(authorities = "PERM_CUSTOMER_MANAGE")
    void deleteCustomer_returns404_whenCustomerNotFound() throws Exception {
        doThrow(new CustomerNotFoundException(99L)).when(customerService).deleteCustomer(99L);

        mockMvc.perform(delete("/api/customers/99").with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteCustomer_returns401_whenUnauthenticated() throws Exception {
        mockMvc.perform(delete("/api/customers/1").with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Customer makeCustomer(Long id, String code, String colorHex) {
        Customer c = new Customer();
        c.setName(code + " Corp");
        c.setCode(code);
        c.setIsActive(true);
        c.setColorHex(colorHex);
        try {
            var f = Customer.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(c, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return c;
    }
}
