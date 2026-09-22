package com.baakio.repository;

import com.baakio.model.PurchaseLine;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PurchaseLineRepository extends JpaRepository<PurchaseLine, Long> {
    List<PurchaseLine> findByPurchaseIdOrderByLineNo(long purchaseId);
}
