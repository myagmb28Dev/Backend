package com.example.pogun.repository.payment;

import com.example.pogun.entity.payment.Purchase;
import com.example.pogun.entity.payment.enums.PurchaseStatus;
import com.example.pogun.entity.user.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PurchaseRepository extends JpaRepository<Purchase, UUID> {
    Optional<Purchase> findByTransactionId(String transactionId);

    Optional<Purchase> findByOriginalTransactionId(String originalTransactionId);

    Optional<Purchase> findByPurchaseToken(String purchaseToken);

    List<Purchase> findByUserAndStatusOrderByCreatedAtAsc(User user, PurchaseStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select purchase from Purchase purchase where purchase.user = :user and purchase.status = :status")
    List<Purchase> findByUserAndStatusForUpdate(@Param("user") User user, @Param("status") PurchaseStatus status);
}
