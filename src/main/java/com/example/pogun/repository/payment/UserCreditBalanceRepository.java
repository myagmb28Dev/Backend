package com.example.pogun.repository.payment;

import com.example.pogun.entity.payment.UserCreditBalance;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserCreditBalanceRepository extends JpaRepository<UserCreditBalance, UUID> {
    Optional<UserCreditBalance> findByUserId(UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select balance from UserCreditBalance balance where balance.user.id = :userId")
    Optional<UserCreditBalance> findByUserIdForUpdate(@Param("userId") UUID userId);
}
