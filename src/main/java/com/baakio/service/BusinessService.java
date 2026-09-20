package com.baakio.service;

import com.baakio.dto.business.BusinessResponse;
import com.baakio.dto.business.PaymentDetailsRequest;
import com.baakio.exception.ApiException;
import com.baakio.model.Business;
import com.baakio.repository.BusinessRepository;
import com.baakio.security.AccountHolderPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class BusinessService {
    private final BusinessRepository businesses;

    public BusinessService(BusinessRepository businesses) {
        this.businesses = businesses;
    }

    public BusinessResponse business(String businessId) {
        Business business = businesses.findById(businessId).orElse(null);
        if (business == null) throw ApiException.notFound();
        return new BusinessResponse(business.getId(), business.getName(), business.getTimezone(),
                business.getCurrency(), business.getUpiVpa(), business.getUpiLinkedMobileE164(),
                business.getUpiPayeeName(), business.getPaymentDetailsAccountHolderConfirmedAt() != null);
    }

    public BusinessResponse updatePaymentDetails(AccountHolderPrincipal accountHolder,
                                                  PaymentDetailsRequest request) {
        String vpa = trimToNull(request.upiVpa());
        String mobile = trimToNull(request.upiLinkedMobileE164());
        String payee = trimToNull(request.upiPayeeName());
        if (vpa != null && mobile != null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PAYMENT_DETAILS_CHOOSE_ONE", "Enter a UPI ID or a UPI-linked mobile number.");
        }
        if (vpa != null && !vpa.matches("[A-Za-z0-9._-]{2,80}@[A-Za-z0-9.-]{2,40}")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_UPI_ID", "Enter a valid UPI ID.");
        }
        if (mobile != null && !mobile.matches("\\+91[6-9][0-9]{9}")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PHONE", "Enter a valid 10-digit Indian mobile number.");
        }
        boolean hasDetails = vpa != null || mobile != null;
        if (hasDetails && !Boolean.TRUE.equals(request.accountHolderConfirmed())) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "ACCOUNT_HOLDER_CONFIRMATION_REQUIRED",
                    "Confirm that these payment details belong to your business.");
        }
        businesses.updatePaymentDetails(accountHolder.businessId(), vpa, mobile, payee,
                hasDetails ? Instant.now() : null);
        return business(accountHolder.businessId());
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
