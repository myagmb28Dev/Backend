package com.example.pogun.repository.payment;

import com.example.pogun.entity.payment.Purchase;
import com.example.pogun.entity.payment.enums.PurchaseStatus;
import com.example.pogun.entity.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PurchaseRepository extends JpaRepository<Purchase, UUID> {
    Optional<Purchase> findByTransactionId(String transactionId);

    Optional<Purchase> findByPurchaseToken(String purchaseToken);

    List<Purchase> findByUserAndStatusOrderByCreatedAtAsc(User user, PurchaseStatus status);
}
