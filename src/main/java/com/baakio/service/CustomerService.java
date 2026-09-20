package com.baakio.service;

import com.baakio.dto.customer.CommitmentSummary;
import com.baakio.dto.customer.CreateCustomerRequest;
import com.baakio.dto.customer.CustomerPage;
import com.baakio.dto.customer.CustomerResponse;
import com.baakio.dto.customer.CommitmentSummary;
import com.baakio.security.AccountHolderPrincipal;
import com.baakio.model.Customer;
import com.baakio.repository.CustomerRepository;
import com.baakio.repository.LedgerRepository;
import com.baakio.utils.MoneyAmounts;
import com.baakio.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class CustomerService {
    private final CustomerRepository customers;
    private final LedgerRepository ledger;

    public CustomerService(CustomerRepository customers, LedgerRepository ledger) {
        this.customers = customers;
        this.ledger = ledger;
    }

    public CustomerResponse create(AccountHolderPrincipal principal, CreateCustomerRequest request) {
        String phone = request.phoneE164() == null || request.phoneE164().isBlank() ? null : request.phoneE164().trim();
        if (phone != null && !phone.startsWith("+")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PHONE", "Enter a phone number in international format, such as +919876543210.");
        }
        long id = customers.create(principal.businessId(), request.displayName().trim(), phone,
                trimToNull(request.notes()), request.preferredMessageLocale());
        return get(principal.businessId(), id);
    }

    public CustomerPage list(AccountHolderPrincipal principal, String search, int requestedPage, int requestedSize) {
        int page = Math.max(0, requestedPage);
        int size = Math.max(1, Math.min(requestedSize, 50));
        String query = search == null ? "" : search.trim();
        long count = customers.countByBusinessAndSearch(principal.businessId(), query);
        List<Long> ids = customers.findIdsByBusinessAndSearch(principal.businessId(), query, size, page * size);
        List<CustomerResponse> items = ids.stream().map(id -> get(principal.businessId(), id)).toList();
        return new CustomerPage(items, page, size, count);
    }

    public CustomerResponse get(String businessId, long customerId) {
        Customer customer = customers.findByBusinessAndId(businessId, customerId);
        if (customer == null) throw ApiException.notFound();
        return mapCustomer(customer);
    }

    public void ensureOwned(String businessId, long customerId) {
        if (!customers.existsByBusinessAndId(businessId, customerId)) throw ApiException.notFound();
    }

    private CustomerResponse mapCustomer(Customer customer) {
        long id = customer.getId();
        CommitmentSummary next = customers.findNextActiveCommitment(customer.getBusinessId(), id,
                ledger.businessDate(customer.getBusinessId()));
        return new CustomerResponse(id, customer.getDisplayName(), customer.getPhoneE164(),
                customer.getNotes(), customer.getPreferredMessageLocale(),
                MoneyAmounts.text(ledger.outstanding(customer.getBusinessId(), id)),
                MoneyAmounts.text(ledger.totalPurchases(customer.getBusinessId(), id)),
                MoneyAmounts.text(ledger.totalPaid(customer.getBusinessId(), id)), next,
                customers.hasOpeningBalance(customer.getBusinessId(), id), customer.getCreatedAt());
    }

    private String trimToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
