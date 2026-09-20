package com.baakio.controller;

import com.baakio.dto.business.BusinessResponse;
import com.baakio.dto.business.PaymentDetailsRequest;
import com.baakio.security.AccountHolderPrincipal;
import com.baakio.service.BusinessService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/business")
public class BusinessController {
    private final BusinessService businesses;

    public BusinessController(BusinessService businesses) {
        this.businesses = businesses;
    }

    @GetMapping
    public BusinessResponse business(@AuthenticationPrincipal AccountHolderPrincipal accountHolder) {
        return businesses.business(accountHolder.businessId());
    }

    @PatchMapping("/payment-details")
    public BusinessResponse updatePaymentDetails(@AuthenticationPrincipal AccountHolderPrincipal accountHolder,
                                                   @Valid @RequestBody PaymentDetailsRequest body) {
        return businesses.updatePaymentDetails(accountHolder, body);
    }
}
