package com.baakio.dto.customer;

import java.util.List;

public record CustomerPage(
        List<CustomerResponse> items,
        int page,
        int pageSize,
        long total
) {}
