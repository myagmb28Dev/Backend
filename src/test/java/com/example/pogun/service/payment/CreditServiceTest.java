package com.example.pogun.service.payment;

import com.example.pogun.dto.common.ApiResponse.ApiException;
import com.example.pogun.dto.user.UserCreditBalanceResponse;
import com.example.pogun.entity.payment.CreditLedgerEntry;
import com.example.pogun.entity.payment.Purchase;
import com.example.pogun.entity.payment.UserCreditBalance;
import com.example.pogun.entity.payment.enums.PaymentPlatform;
import com.example.pogun.entity.payment.enums.PurchaseStatus;
import com.example.pogun.entity.user.User;
import com.example.pogun.entity.user.enums.UserRole;
import com.example.pogun.entity.user.enums.UserStatus;
import com.example.pogun.repository.payment.CreditLedgerRepository;
import com.example.pogun.repository.payment.PurchaseRepository;
import com.example.pogun.repository.payment.UserCreditBalanceRepository;
import com.example.pogun.service.user.CurrentUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreditServiceTest {

    @Mock
    private UserCreditBalanceRepository userCreditBalanceRepository;
    @Mock
    private CreditLedgerRepository creditLedgerRepository;
    @Mock
    private PurchaseRepository purchaseRepository;
    @Mock
    private CurrentUserService currentUserService;

    private CreditService creditService;

    @BeforeEach
    void setUp() {
        creditService = new CreditService(userCreditBalanceRepository, creditLedgerRepository, purchaseRepository, currentUserService);
    }

    @Test
    void getCurrentBalance_defaultsToZeroWhenNoBalanceRowExists() {
        User user = user();
        when(currentUserService.getCurrentUser()).thenReturn(user);
        when(userCreditBalanceRepository.findByUserId(user.getId())).thenReturn(Optional.empty());

        UserCreditBalanceResponse response = creditService.getCurrentBalance();

        assertThat(response.balance()).isEqualTo(0);
    }

    @Test
    void useCreditsForAi_deductsAndWritesLedger() {
        User user = user();
        UserCreditBalance balance = UserCreditBalance.builder()
                .id(UUID.randomUUID())
                .user(user)
                .balance(5)
                .build();

        when(currentUserService.getCurrentUser()).thenReturn(user);
        when(userCreditBalanceRepository.findByUserIdForUpdate(user.getId())).thenReturn(Optional.of(balance));
        when(userCreditBalanceRepository.save(balance)).thenReturn(balance);

        int after = creditService.useCreditsForAi(AiCreditPolicy.REQUEST_300, "MISSING_PET_ANALYSIS", "req-1", "hello");

        assertThat(after).isEqualTo(4);
        assertThat(balance.getBalance()).isEqualTo(4);
        ArgumentCaptor<CreditLedgerEntry> captor = ArgumentCaptor.forClass(CreditLedgerEntry.class);
        verify(creditLedgerRepository).save(captor.capture());
        assertThat(captor.getValue().getDelta()).isEqualTo(-1);
        assertThat(captor.getValue().getReferenceId()).isEqualTo("req-1");
    }

    @Test
    void useCreditsForAi_rejectsTooLongRequest() {
        String content = "a".repeat(301);

        assertThatThrownBy(() -> creditService.useCreditsForAi(AiCreditPolicy.REQUEST_300, "ANY", "req-1", content))
                .isInstanceOf(ApiException.class)
                .hasMessage("AI 요청은 최대 300자까지 허용됩니다.");
    }

    @Test
    void useCreditsForAi_rejectsWhenBalanceIsInsufficient() {
        User user = user();
        UserCreditBalance balance = UserCreditBalance.builder()
                .id(UUID.randomUUID())
                .user(user)
                .balance(0)
                .build();

        when(currentUserService.getCurrentUser()).thenReturn(user);
        when(userCreditBalanceRepository.findByUserIdForUpdate(user.getId())).thenReturn(Optional.of(balance));

        assertThatThrownBy(() -> creditService.useCreditsForAi(AiCreditPolicy.REQUEST_500, "ANY", "req-2", "short"))
                .isInstanceOf(ApiException.class)
                .hasMessage("크레딧이 부족합니다.");
    }

    @Test
    void useMissingPetAnalysisCredit_prefersShorterEligiblePackageFirst() {
        User user = user();
        UserCreditBalance balance = UserCreditBalance.builder()
                .id(UUID.randomUUID())
                .user(user)
                .balance(2)
                .build();
        Purchase pack300 = Purchase.builder()
                .id(UUID.randomUUID())
                .user(user)
                .platform(PaymentPlatform.IOS)
                .status(PurchaseStatus.VERIFIED)
                .productId("paw_ai_credits_3_300")
                .creditedCredits(3)
                .consumedCredits(0)
                .build();
        pack300.setCreatedAt(Instant.parse("2026-06-16T00:00:00Z"));
        Purchase pack500 = Purchase.builder()
                .id(UUID.randomUUID())
                .user(user)
                .platform(PaymentPlatform.IOS)
                .status(PurchaseStatus.VERIFIED)
                .productId("paw_ai_credits_5_500")
                .creditedCredits(5)
                .consumedCredits(0)
                .build();
        pack500.setCreatedAt(Instant.parse("2026-06-15T00:00:00Z"));

        when(currentUserService.getCurrentUser()).thenReturn(user);
        when(purchaseRepository.findByUserAndStatusForUpdate(user, PurchaseStatus.VERIFIED))
                .thenReturn(java.util.List.of(pack500, pack300));
        when(userCreditBalanceRepository.findByUserIdForUpdate(user.getId())).thenReturn(Optional.of(balance));
        when(userCreditBalanceRepository.save(balance)).thenReturn(balance);

        CreditService.ManualAnalysisCreditUsage result = creditService.useMissingPetAnalysisCredit("notice-1", "a".repeat(250));

        assertThat(result.productId()).isEqualTo("paw_ai_credits_3_300");
        assertThat(result.appliedMaxDescriptionLength()).isEqualTo(300);
        assertThat(pack300.getConsumedCredits()).isEqualTo(1);
        assertThat(balance.getBalance()).isEqualTo(1);
    }

    @Test
    void useMissingPetAnalysisCredit_rejectsWhenNoPackageMatchesDescriptionLength() {
        User user = user();
        Purchase pack300 = Purchase.builder()
                .id(UUID.randomUUID())
                .user(user)
                .platform(PaymentPlatform.IOS)
                .status(PurchaseStatus.VERIFIED)
                .productId("paw_ai_credits_3_300")
                .creditedCredits(3)
                .consumedCredits(0)
                .build();

        when(currentUserService.getCurrentUser()).thenReturn(user);
        when(purchaseRepository.findByUserAndStatusForUpdate(user, PurchaseStatus.VERIFIED))
                .thenReturn(java.util.List.of(pack300));

        assertThatThrownBy(() -> creditService.useMissingPetAnalysisCredit("notice-2", "a".repeat(400)))
                .isInstanceOf(ApiException.class)
                .hasMessage("사용 가능한 분석 요청권이 없거나 공고 설명 길이에 맞는 상품이 없습니다.");
    }

    private User user() {
        return User.builder()
                .id(UUID.randomUUID())
                .firebaseUid("firebase-uid")
                .email("user@example.com")
                .nickname("tester")
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .build();
    }
}
