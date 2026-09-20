package com.baakio.dto.dashboard;

import java.util.List;

import com.baakio.dto.customer.CustomerResponse;

public record DashboardResponse(
        String businessDate,
        String totalOutstanding,
        int customersWithOutstanding,
        String dueTodayAmount,
        int dueTodayCount,
        String overdueAmount,
        int overdueCount,
        int customersWithoutPromise,
        List<CustomerResponse> attentionCustomers
) {}
