package com.example.pogun.repository.payment;

import com.example.pogun.entity.payment.CreditLedgerEntry;
import com.example.pogun.entity.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CreditLedgerRepository extends JpaRepository<CreditLedgerEntry, UUID> {
    List<CreditLedgerEntry> findTop50ByUserOrderByCreatedAtDesc(User user);
}
