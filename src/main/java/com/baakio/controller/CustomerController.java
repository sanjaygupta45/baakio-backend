package com.baakio.controller;

import com.baakio.dto.customer.CreateCustomerRequest;
import com.baakio.dto.customer.CustomerPage;
import com.baakio.dto.customer.CustomerResponse;
import com.baakio.service.CustomerService;
import com.baakio.security.AccountHolderPrincipal;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/customers")
public class CustomerController {
    private final CustomerService customers;

    public CustomerController(CustomerService customers) { this.customers = customers; }

    @PostMapping
    public CustomerResponse create(@AuthenticationPrincipal AccountHolderPrincipal principal,
                                           @Valid @RequestBody CreateCustomerRequest body) {
        return customers.create(principal, body);
    }

    @GetMapping
    public CustomerPage list(@AuthenticationPrincipal AccountHolderPrincipal principal,
                                     @RequestParam(defaultValue = "") String search,
                                     @RequestParam(defaultValue = "0") int page,
                                     @RequestParam(defaultValue = "20") int size) {
        return customers.list(principal, search, page, size);
    }

    @GetMapping("/{customerId}")
    public CustomerResponse get(@AuthenticationPrincipal AccountHolderPrincipal principal,
                                        @PathVariable long customerId) {
        return customers.get(principal.businessId(), customerId);
    }
}
