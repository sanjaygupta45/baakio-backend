package com.baakio.service;

import com.baakio.exception.ApiException;
import com.baakio.model.Business;
import com.baakio.repository.AccountOpeningBalanceRepository;
import com.baakio.repository.BusinessRepository;
import com.baakio.repository.CommitmentRepository;
import com.baakio.repository.CustomerRepository;
import com.baakio.repository.PaymentRepository;
import com.baakio.repository.PurchaseRepository;
import com.baakio.utils.MoneyAmounts;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;

@Service
public class LedgerService {
    private final AccountOpeningBalanceRepository openings;
    private final BusinessRepository businesses;
    private final CommitmentRepository commitments;
    private final CustomerRepository customers;
    private final PaymentRepository payments;
    private final PurchaseRepository purchases;

    public LedgerService(AccountOpeningBalanceRepository openings, BusinessRepository businesses,
                         CommitmentRepository commitments, CustomerRepository customers,
                         PaymentRepository payments, PurchaseRepository purchases) {
        this.openings = openings;
        this.businesses = businesses;
        this.commitments = commitments;
        this.customers = customers;
        this.payments = payments;
        this.purchases = purchases;
    }

    @Transactional
    public void lockCustomer(String businessId, long customerId) {
        customers.findLockedByBusinessIdAndId(businessId, customerId).orElseThrow(ApiException::notFound);
    }

    public BigDecimal outstanding(String businessId, long customerId) {
        BigDecimal opening = openings.findByBusinessIdAndCustomerId(businessId, customerId)
                .map(value -> value.getAmount()).orElse(MoneyAmounts.zero());
        return opening.add(purchases.sumTotal(businessId, customerId))
                .subtract(payments.sumConfirmed(businessId, customerId)).setScale(2);
    }

    public BigDecimal totalPurchases(String businessId, long customerId) {
        return purchases.sumTotal(businessId, customerId).setScale(2);
    }

    public BigDecimal totalPaid(String businessId, long customerId) {
        return payments.sumConfirmed(businessId, customerId).setScale(2);
    }

    public BigDecimal outstandingThrough(String businessId, long customerId, LocalDate date) {
        BigDecimal opening = openings
                .findFirstByBusinessIdAndCustomerIdAndCutoffDateLessThanEqualOrderByCutoffDateDesc(businessId, customerId, date)
                .map(value -> value.getAmount()).orElse(MoneyAmounts.zero());
        return opening.add(purchases.sumBeforeOrOn(businessId, customerId, date))
                .subtract(payments.sumBeforeOrOn(businessId, customerId, date)).setScale(2);
    }

    public BigDecimal opening(String businessId, long customerId) {
        return openings.findByBusinessIdAndCustomerId(businessId, customerId)
                .map(value -> value.getAmount()).orElse(MoneyAmounts.zero()).setScale(2);
    }

    public BigDecimal activeCommitmentRemaining(String businessId, long customerId) {
        BigDecimal remaining = MoneyAmounts.zero();
        for (Object[] row : commitments.findActiveAmounts(businessId, customerId)) {
            remaining = remaining.add((BigDecimal) row[0]).subtract((BigDecimal) row[1]);
        }
        return remaining.setScale(2);
    }

    public LocalDate businessDate(String businessId) {
        Business business = businesses.findById(businessId).orElse(null);
        try {
            return LocalDate.now(ZoneId.of(business == null ? null : business.getTimezone()));
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "BUSINESS_TIMEZONE_INVALID",
                    "The shop time zone is not configured correctly.");
        }
    }
}
