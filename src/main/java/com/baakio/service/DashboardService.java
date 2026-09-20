package com.baakio.service;

import com.baakio.dto.customer.CustomerResponse;
import com.baakio.dto.customer.CommitmentSummary;
import com.baakio.dto.dashboard.DashboardResponse;
import com.baakio.repository.DashboardRepository;
import com.baakio.repository.LedgerRepository;
import com.baakio.utils.MoneyAmounts;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class DashboardService {
    private final DashboardRepository dashboardRepository;
    private final LedgerRepository ledger;
    private final CustomerService customers;

    public DashboardService(DashboardRepository dashboardRepository, LedgerRepository ledger, CustomerService customers) {
        this.dashboardRepository = dashboardRepository;
        this.ledger = ledger;
        this.customers = customers;
    }

    public DashboardResponse get(String businessId, LocalDate requestedDate) {
        LocalDate today = requestedDate == null ? ledger.businessDate(businessId) : requestedDate;
        if (today.isAfter(ledger.businessDate(businessId))) today = ledger.businessDate(businessId);
        List<Long> ids = dashboardRepository.findCustomerIds(businessId);
        BigDecimal totalOutstanding = MoneyAmounts.zero();
        int customersWithOutstanding = 0;
        BigDecimal dueTodayAmount = MoneyAmounts.zero();
        BigDecimal overdueAmount = MoneyAmounts.zero();
        int dueTodayCount = 0;
        int overdueCount = 0;
        int withoutPromise = 0;
        List<RankedCustomer> ranked = new ArrayList<>();
        for (Long id : ids) {
            BigDecimal outstanding = ledger.outstanding(businessId, id);
            totalOutstanding = totalOutstanding.add(outstanding);
            if (outstanding.signum() > 0) customersWithOutstanding++;
            List<CommitmentSummary> promises = dashboardRepository.findActivePromises(businessId, id, today);
            BigDecimal activeRemaining = MoneyAmounts.zero();
            for (CommitmentSummary promise : promises) {
                BigDecimal remaining = new BigDecimal(promise.remaining());
                if (remaining.signum() <= 0) continue;
                activeRemaining = activeRemaining.add(remaining);
                if ("OVERDUE".equals(promise.status())) {
                    overdueAmount = overdueAmount.add(remaining);
                    overdueCount++;
                } else if ("DUE_TODAY".equals(promise.status())) {
                    dueTodayAmount = dueTodayAmount.add(remaining);
                    dueTodayCount++;
                }
            }
            if (outstanding.signum() > 0 && activeRemaining.signum() == 0) withoutPromise++;
            if (outstanding.signum() > 0) {
                CustomerResponse customer = customers.get(businessId, id);
                ranked.add(new RankedCustomer(customer, priority(customer, today)));
            }
        }
        List<CustomerResponse> attention = ranked.stream()
                .sorted(Comparator.comparingInt(RankedCustomer::priority)
                        .thenComparing(c -> c.customer().nextCommitment() == null ? "9999-12-31" : c.customer().nextCommitment().dueDate().toString())
                        .thenComparing(c -> new BigDecimal(c.customer().outstanding()).negate()))
                .limit(12).map(RankedCustomer::customer).toList();
        return new DashboardResponse(today.toString(), MoneyAmounts.text(totalOutstanding), customersWithOutstanding,
                MoneyAmounts.text(dueTodayAmount), dueTodayCount, MoneyAmounts.text(overdueAmount), overdueCount,
                withoutPromise, attention);
    }

    private int priority(CustomerResponse customer, LocalDate today) {
        if (customer.nextCommitment() == null) return 3;
        if (customer.nextCommitment().dueDate().isBefore(today)) return 1;
        if (customer.nextCommitment().dueDate().equals(today)) return 2;
        return 4;
    }

    private record RankedCustomer(CustomerResponse customer, int priority) {}
}
