package com.caseflow.customer.api;

import com.caseflow.auth.CaseFlowUserDetailsService;
import com.caseflow.auth.JwtTokenService;
import com.caseflow.common.exception.CustomerNotFoundException;
import com.caseflow.common.security.SecurityConfig;
import com.caseflow.customer.api.dto.CreateCustomerRequest;
import com.caseflow.customer.api.dto.CustomerResponse;
import com.caseflow.customer.api.dto.CustomerSummaryResponse;
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
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

    @Test
    @WithMockUser(roles = "VIEWER")
    void getById_returns200_whenFound() throws Exception {
        Customer c = makeCustomer(1L, "ACME");
        CustomerResponse response = new CustomerResponse(1L, "ACME Corp", "ACME", true, Instant.now(), Instant.now());

        when(customerService.getById(1L)).thenReturn(c);
        when(customerMapper.toResponse(c)).thenReturn(response);

        mockMvc.perform(get("/api/customers/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("ACME Corp"));
    }

    @Test
    @WithMockUser(roles = "VIEWER")
    void getById_returns404_whenNotFound() throws Exception {
        when(customerService.getById(99L)).thenThrow(new CustomerNotFoundException(99L));

        mockMvc.perform(get("/api/customers/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @WithMockUser(roles = "VIEWER")
    void listCustomers_returns200_withSummaryList() throws Exception {
        Customer c = makeCustomer(1L, "ACME");
        CustomerSummaryResponse summary = new CustomerSummaryResponse(1L, "ACME Corp", "ACME");

        when(customerService.findAll()).thenReturn(List.of(c));
        when(customerMapper.toSummaryResponse(c)).thenReturn(summary);

        mockMvc.perform(get("/api/customers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("ACME"));
    }

    @Test
    @WithMockUser(roles = "AGENT")
    void createCustomer_returns201_withValidRequest() throws Exception {
        CreateCustomerRequest request = new CreateCustomerRequest("ACME Corp", "ACME");
        Customer customer = makeCustomer(1L, "ACME");
        CustomerResponse response = new CustomerResponse(1L, "ACME Corp", "ACME", true, Instant.now(), Instant.now());

        when(customerService.createCustomer(any(), any())).thenReturn(customer);
        when(customerMapper.toResponse(customer)).thenReturn(response);

        mockMvc.perform(post("/api/customers")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void listCustomers_returns401_whenUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/customers"))
                .andExpect(status().isUnauthorized());
    }

    private Customer makeCustomer(Long id, String code) {
        Customer c = new Customer();
        c.setName(code + " Corp");
        c.setCode(code);
        c.setIsActive(true);
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
