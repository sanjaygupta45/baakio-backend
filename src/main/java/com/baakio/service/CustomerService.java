package com.baakio.service;

import com.baakio.dto.customer.CommitmentSummary;
import com.baakio.dto.customer.CreateCustomerRequest;
import com.baakio.dto.customer.CustomerPage;
import com.baakio.dto.customer.CustomerResponse;
import com.baakio.security.AccountHolderPrincipal;
import com.baakio.model.Customer;
import com.baakio.repository.AccountOpeningBalanceRepository;
import com.baakio.repository.CommitmentRepository;
import com.baakio.repository.CustomerRepository;
import org.springframework.data.domain.PageRequest;
import com.baakio.utils.MoneyAmounts;
import com.baakio.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CustomerService {
    private final CustomerRepository customers;
    private final CommitmentRepository commitments;
    private final AccountOpeningBalanceRepository openings;
    private final LedgerService ledger;

    public CustomerService(CustomerRepository customers, CommitmentRepository commitments,
                           AccountOpeningBalanceRepository openings, LedgerService ledger) {
        this.customers = customers;
        this.commitments = commitments;
        this.openings = openings;
        this.ledger = ledger;
    }

    public CustomerResponse create(AccountHolderPrincipal principal, CreateCustomerRequest request) {
        String phone = request.phoneE164() == null || request.phoneE164().isBlank() ? null : request.phoneE164().trim();
        if (phone != null && !phone.startsWith("+")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PHONE", "Enter a phone number in international format, such as +919876543210.");
        }
        long id = customers.saveAndFlush(new Customer(principal.businessId(), request.displayName().trim(), phone,
                trimToNull(request.notes()), request.preferredMessageLocale())).getId();
        return get(principal.businessId(), id);
    }

    public CustomerPage list(AccountHolderPrincipal principal, String search, int requestedPage, int requestedSize) {
        int page = Math.max(0, requestedPage);
        int size = Math.max(1, Math.min(requestedSize, 50));
        String query = search == null ? "" : search.trim();
        String pattern = "%" + query.toLowerCase() + "%";
        long count = customers.countByBusinessAndSearch(principal.businessId(), query, pattern);
        List<Long> ids = customers.findIdsByBusinessAndSearch(principal.businessId(), query,
                pattern, PageRequest.of(page, size));
        List<CustomerResponse> items = ids.stream().map(id -> get(principal.businessId(), id)).toList();
        return new CustomerPage(items, page, size, count);
    }

    public CustomerResponse get(String businessId, long customerId) {
        Customer customer = customers.findByBusinessIdAndId(businessId, customerId).orElseThrow(ApiException::notFound);
        return mapCustomer(customer);
    }

    public void ensureOwned(String businessId, long customerId) {
        if (!customers.existsByBusinessIdAndId(businessId, customerId)) throw ApiException.notFound();
    }

    private CustomerResponse mapCustomer(Customer customer) {
        long id = customer.getId();
        CommitmentSummary next = commitments.findActiveSummaries(customer.getBusinessId(), id,
                ledger.businessDate(customer.getBusinessId())).stream().findFirst().orElse(null);
        return new CustomerResponse(id, customer.getDisplayName(), customer.getPhoneE164(),
                customer.getNotes(), customer.getPreferredMessageLocale(),
                MoneyAmounts.text(ledger.outstanding(customer.getBusinessId(), id)),
                MoneyAmounts.text(ledger.totalPurchases(customer.getBusinessId(), id)),
                MoneyAmounts.text(ledger.totalPaid(customer.getBusinessId(), id)), next,
                openings.existsByBusinessIdAndCustomerId(customer.getBusinessId(), id), customer.getCreatedAt());
    }

    private String trimToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
